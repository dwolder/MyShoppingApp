# MyShoppingList

A Canadian grocery shopping list app for Android with family sharing and store price lookups.

## Features

- Create and manage multiple shopping lists
- Items organized by grocery category (Produce, Dairy, Meat, etc.)
- Swipe to delete, tap to check off items
- Family sharing via Supabase (real-time sync)
- Search products at Canadian grocery stores (Superstore, Metro, FreshCo, Sobeys)
- See which brands are on sale via Flipp flyer data

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Android UI)
- **Room** (local SQLite database for offline-first)
- **Supabase** (cloud sync, auth, realtime, edge functions)
- **Hilt** (dependency injection)
- **Ktor** (HTTP client)

## Getting Started

### Prerequisites

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug 2024.2 or newer)
2. An Android phone with USB debugging enabled, or use the emulator

### Setup

1. Clone this repository
2. Open the project in Android Studio
3. Copy `local.properties.example` to `local.properties`
4. Fill in your Android SDK path (Android Studio usually does this automatically)
5. Click "Sync Project with Gradle Files" in the toolbar
6. Run the app on your device/emulator

### Supabase Setup (for cloud sync / family sharing)

The app works fully offline without Supabase. To enable cloud sync:

1. Create a free account at [supabase.com](https://supabase.com)
2. Create a new project
3. Go to SQL Editor and run the migrations in `supabase/migrations/` in order
4. Go to Project Settings > API and copy your URL and anon key
5. Add them to `local.properties`:
   ```
   SUPABASE_URL=https://YOUR_PROJECT.supabase.co
   SUPABASE_ANON_KEY=your-anon-key
   ```
6. Deploy the edge function:
   ```bash
   supabase functions deploy grocery-search
   ```

### Edge Function Environment Variables

Set these in Supabase Dashboard > Edge Functions > grocery-search > Secrets:

- `PC_STORE_ID` - Your local Superstore number (find it on the store locator)
- `PC_API_KEY` - API key from PC Express app (default provided works for basic searches)

## Project Structure

```
app/src/main/java/com/myshoppinglist/
  data/
    local/          Room database, DAOs, entities
    remote/         Supabase client, grocery API service, sync
    repository/     Repository pattern (local + remote)
  ui/
    theme/          Material 3 theme
    screens/        Composable screens (lists, items, store search)
    viewmodel/      ViewModels
    navigation/     Navigation routes
  di/               Hilt dependency injection modules

supabase/
  migrations/       SQL to set up the database schema + RLS
  functions/
    grocery-search/ Edge function that proxies grocery store APIs
```

## Grocery Store API Notes

Canadian grocery chains don't offer public APIs. This app uses:

- **PC Express API** (`api.pcexpress.ca`) for Superstore product search
- **Metro API** (`api.metro.ca`) for Metro product search
- **Flipp API** (`backflipp.wishabi.com`) for FreshCo/Sobeys flyer/sale data

These are undocumented APIs. If they break, the app still works as a shopping list --
the store search just shows "no results".

## Phone Setup for Testing

1. On your Android phone, go to Settings > About Phone
2. Tap "Build Number" 7 times to enable Developer Options
3. Go to Settings > Developer Options
4. Enable "USB Debugging"
5. Connect your phone via USB and authorize the computer
6. In Android Studio, select your phone from the device dropdown and click Run
