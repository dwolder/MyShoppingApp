-- Function to join a family by invite code
CREATE OR REPLACE FUNCTION join_family(p_invite_code TEXT)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
    v_family_id UUID;
BEGIN
    SELECT id INTO v_family_id
    FROM public.families
    WHERE invite_code = p_invite_code;

    IF v_family_id IS NULL THEN
        RAISE EXCEPTION 'Invalid invite code';
    END IF;

    UPDATE public.profiles
    SET family_id = v_family_id, updated_at = now()
    WHERE id = auth.uid();

    RETURN v_family_id;
END;
$$;
