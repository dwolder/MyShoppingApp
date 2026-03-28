-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Families table: groups of users who share lists
CREATE TABLE families (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    invite_code TEXT UNIQUE NOT NULL DEFAULT substr(md5(random()::text), 1, 8),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID REFERENCES auth.users(id)
);

-- Profiles: extends auth.users with app-specific data
CREATE TABLE profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL DEFAULT '',
    family_id UUID REFERENCES families(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Shopping lists
CREATE TABLE shopping_lists (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    family_id UUID REFERENCES families(id),
    created_by UUID REFERENCES auth.users(id),
    local_id TEXT UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- List items
CREATE TABLE list_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    list_id UUID NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    quantity DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    unit TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL DEFAULT 'Other',
    is_checked BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT NOT NULL DEFAULT '',
    local_id TEXT UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes for performance
CREATE INDEX idx_list_items_list_id ON list_items(list_id);
CREATE INDEX idx_shopping_lists_family_id ON shopping_lists(family_id);
CREATE INDEX idx_profiles_family_id ON profiles(family_id);

-- Row Level Security policies

ALTER TABLE families ENABLE ROW LEVEL SECURITY;
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE shopping_lists ENABLE ROW LEVEL SECURITY;
ALTER TABLE list_items ENABLE ROW LEVEL SECURITY;

-- Profiles: users can read/update their own profile
CREATE POLICY "Users can view own profile"
    ON profiles FOR SELECT
    USING (auth.uid() = id);

CREATE POLICY "Users can update own profile"
    ON profiles FOR UPDATE
    USING (auth.uid() = id);

CREATE POLICY "Users can insert own profile"
    ON profiles FOR INSERT
    WITH CHECK (auth.uid() = id);

-- Families: members can view their own family
CREATE POLICY "Family members can view their family"
    ON families FOR SELECT
    USING (
        id IN (SELECT family_id FROM profiles WHERE id = auth.uid())
    );

CREATE POLICY "Authenticated users can create families"
    ON families FOR INSERT
    WITH CHECK (auth.uid() = created_by);

-- Shopping lists: accessible by family members or the creator
CREATE POLICY "Users can view their family lists"
    ON shopping_lists FOR SELECT
    USING (
        created_by = auth.uid()
        OR family_id IN (SELECT family_id FROM profiles WHERE id = auth.uid())
    );

CREATE POLICY "Users can create lists"
    ON shopping_lists FOR INSERT
    WITH CHECK (auth.uid() = created_by);

CREATE POLICY "Users can update their family lists"
    ON shopping_lists FOR UPDATE
    USING (
        created_by = auth.uid()
        OR family_id IN (SELECT family_id FROM profiles WHERE id = auth.uid())
    );

CREATE POLICY "Users can delete their own lists"
    ON shopping_lists FOR DELETE
    USING (created_by = auth.uid());

-- List items: accessible if the parent list is accessible
CREATE POLICY "Users can view items in accessible lists"
    ON list_items FOR SELECT
    USING (
        list_id IN (
            SELECT id FROM shopping_lists
            WHERE created_by = auth.uid()
            OR family_id IN (SELECT family_id FROM profiles WHERE id = auth.uid())
        )
    );

CREATE POLICY "Users can insert items in accessible lists"
    ON list_items FOR INSERT
    WITH CHECK (
        list_id IN (
            SELECT id FROM shopping_lists
            WHERE created_by = auth.uid()
            OR family_id IN (SELECT family_id FROM profiles WHERE id = auth.uid())
        )
    );

CREATE POLICY "Users can update items in accessible lists"
    ON list_items FOR UPDATE
    USING (
        list_id IN (
            SELECT id FROM shopping_lists
            WHERE created_by = auth.uid()
            OR family_id IN (SELECT family_id FROM profiles WHERE id = auth.uid())
        )
    );

CREATE POLICY "Users can delete items in accessible lists"
    ON list_items FOR DELETE
    USING (
        list_id IN (
            SELECT id FROM shopping_lists
            WHERE created_by = auth.uid()
            OR family_id IN (SELECT family_id FROM profiles WHERE id = auth.uid())
        )
    );

-- Auto-create profile on user signup
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = ''
AS $$
BEGIN
    INSERT INTO public.profiles (id, display_name)
    VALUES (NEW.id, COALESCE(NEW.raw_user_meta_data->>'display_name', ''));
    RETURN NEW;
END;
$$;

CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION handle_new_user();

-- Enable realtime for list_items so family members see live updates
ALTER PUBLICATION supabase_realtime ADD TABLE list_items;
ALTER PUBLICATION supabase_realtime ADD TABLE shopping_lists;
