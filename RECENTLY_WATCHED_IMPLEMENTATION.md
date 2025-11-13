# Recently Watched Implementation

## Overview
This implementation adds comprehensive recently watched functionality to the HomeFlixTV Android app, matching the behavior shown in your API response.

## Features Implemented

### 1. Home Screen Recently Watched Cards
- **Continue Watching Row**: Displays recently watched media with progress indicators
- **Progress Visualization**: Shows percentage watched and progress bar
- **Last Watched Time**: Displays when the media was last watched (e.g., "2h ago", "1d ago")
- **Thumbnail Display**: Shows media thumbnails with overlay information

### 2. Details Screen Playback Options
- **Continue Watching Button**: Primary button when progress > 5%
- **Play from Beginning Button**: Secondary option when progress exists
- **Progress Indicator**: Shows current watch progress with visual progress bar
- **Smart Button Logic**: Shows regular "Play" button when no progress exists

### 3. Video Player Resume Functionality
- **Resume from Progress**: Automatically starts from saved position
- **Force Start from Beginning**: Option to restart from 0:00
- **Progress Saving**: Saves progress on player close
- **API Integration**: Uses `/api/playback/recently-watched` endpoint

## API Integration

### Recently Watched Endpoint
```
GET /api/playback/recently-watched
Header: X-User-ID: 1
```

**Response Structure:**
```json
[
  {
    "id": 353,
    "media_id": 1515,
    "user_id": "1",
    "last_watched_at": "2025-11-13T07:20:52+06:00",
    "progress_seconds": 1209,
    "duration_seconds": 3545,
    "media": { /* Media object */ }
  }
]
```

### Progress Update Endpoint
```
POST /api/playback/progress
Header: X-User-ID: 1
Body: {
  "media_id": 1515,
  "position": 1209,
  "duration": 3545
}
```

## Implementation Details

### 1. Data Models
- **RecentlyWatchedItem**: Domain model for recently watched items
- **ContinueWatchingItem**: UI model for continue watching cards
- **Media**: Enhanced to support both movies and episodes

### 2. Repository Layer
- **MediaRepository.getRecentlyWatchedWithProgress()**: Fetches recently watched items
- **MediaRepository.updatePlaybackProgress()**: Saves playback progress
- **Flow-based API**: Reactive data loading

### 3. UI Components
- **ContinueWatchingRow**: Horizontal scrolling row of recently watched items
- **ContinueWatchingCard**: Individual card with progress and controls
- **Enhanced DetailsScreen**: Smart playback buttons based on progress
- **VideoPlayer**: Resume functionality with saved progress

### 4. Navigation
- **Enhanced VideoPlayer Route**: Supports resume and force-start parameters
- **Smart Navigation**: Passes appropriate parameters based on user choice

## Usage

### Home Screen
The home screen automatically displays recently watched items in a dedicated row. Users can:
- Click to continue watching from saved progress
- See visual progress indicators
- View last watched timestamps

### Details Screen
When viewing media details, users see:
- **Continue Watching** button (primary) if progress > 5%
- **Play from Beginning** button (secondary) if progress exists
- **Regular Play** button if no progress exists
- Visual progress bar showing current progress

### Video Player
The video player automatically:
- Resumes from saved position when "Continue Watching" is selected
- Starts from beginning when "Play from Beginning" is selected
- Saves progress when player is closed
- Updates the recently watched list

## File Changes

### New/Modified Files:
1. **DetailsScreen.kt** - Added continue watching/play from beginning buttons
2. **DetailsViewModel.kt** - Added watch progress loading
3. **VideoPlayerScreen.kt** - Added resume functionality
4. **VideoPlayerViewModel.kt** - Added progress loading and saving
5. **HomeFlixNavigation.kt** - Enhanced navigation with resume parameters
6. **Media.kt** - Added episode support and TMDB fields
7. **MediaDto.kt** - Added episode fields and enhanced mapping
8. **ContinueWatchingRow.kt** - Enhanced with last watched time display

### API Integration:
- **MediaRepository.kt** - Added recently watched methods
- **HomeFlixApiService.kt** - Added recently watched endpoints
- **PlaybackDto.kt** - Added recently watched DTOs

## Testing

To test the implementation:

1. **Start watching a movie/episode** - Play for a few minutes then close
2. **Check home screen** - Should show in "Continue Watching" row
3. **Open details screen** - Should show "Continue Watching" and "Play from Beginning" buttons
4. **Test resume** - Click "Continue Watching" should resume from saved position
5. **Test restart** - Click "Play from Beginning" should start from 0:00

## Configuration

The implementation uses the existing API base URL configuration and user ID header (`X-User-ID: 1`). No additional configuration is required.

## Performance

- **Cached Loading**: Recently watched data is cached for performance
- **Background Updates**: Progress is saved asynchronously
- **Efficient API Calls**: Minimal API calls during playback
- **Smart Loading**: Only loads progress when needed

## Future Enhancements

Potential future improvements:
- Multiple user support
- Watch history management
- Progress sync across devices
- Offline progress storage
- Advanced progress analytics