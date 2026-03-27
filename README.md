# 🎬 HomeFlix TV - Netflix-Style Android TV App

**Version 2.0** - Professional Android TV streaming application with modern UI/UX, featuring ultra-fast LAN streaming, intelligent caching, smooth D-pad navigation, and professional-grade video playback. Built with Jetpack Compose and optimized for the bigs screen experience.

![Android TV](https://img.shields.io/badge/Android-TV-3DDC84?style=flat&logo=android) ![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=flat&logo=kotlin) ![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat&logo=jetpack-compose) ![ExoPlayer](https://img.shields.io/badge/ExoPlayer-FF0000?style=flat&logo=youtube) ![Material3](https://img.shields.io/badge/Material%203-1976D2?style=flat&logo=material-design) ![Version](https://img.shields.io/badge/version-2.0-blue)

## 📱 App Preview

![HomeFlix TV Preview](preview-app.gif)

### 🎯 Netflix-Level Features
- **Auto-sliding hero section** with crossfade animations and staggered content reveal
- **Smooth D-pad navigation** optimized for TV remotes with professional focus management
- **Continue Watching** with progress tracking and resume functionality
- **Professional video player** with enhanced subtitle support and customizable styling
- **Intelligent caching system** for instant app startup and offline support
- **TV series support** with season/episode navigation and autoplay
- **My List** functionality for personalized content management
- **Smart recommendations** with multiple algorithm endpoints
- **Clean, modern UI** following Material Design 3 with Netflix-inspired theming

## 🌟 HomeFlix Ecosystem

HomeFlix TV is part of the comprehensive **HomeFlix streaming platform ecosystem**:

- **🏠 HomeFlix Web Backend** - Full-featured media server with torrent downloads
- **📱 HomeFlix TV** - Android TV client (this repository)  
- **🌐 Web Interface** - Netflix-style web UI for browser access

🔗 **Backend Repository**: [HomeFlix Web App](https://github.com/azad25/homeflix-wifi)

### 🎥 Complete Streaming Solution
The HomeFlix ecosystem provides a professional streaming experience with:
- **One-click torrent downloads** directly from TMDB movie pages
- **Real-time download progress** tracking with speed and ETA
- **Automatic library integration** for downloaded content
- **Multi-source torrent search** with quality filtering (4K, 1080p, 720p)
- **Smart media management** with automatic metadata fetching
- **Netflix-style interface** across all platforms
- **Cross-platform compatibility** (Web, Android TV, Mobile)

## ✨ Features

### 🎥 Advanced Video Streaming
- **Ultra-fast LAN streaming** with instant playback and zero-copy sendfile optimization
- **ExoPlayer 3 integration** with professional-grade video rendering and hardware acceleration
- **Multiple format support** (MP4, MKV, AVI, MOV, WMV) with automatic transcoding
- **Adaptive streaming** with automatic quality adjustment and buffer management
- **Resume playback** from last watched position with progress sync
- **Enhanced subtitle support** with multiple formats (SRT, VTT, ASS/SSA) and customizable styling
- **Progress tracking** with automatic save on exit and real-time sync
- **Netflix-red themed player** with smooth controls and auto-hiding UI
- **Episode autoplay** for TV series with next episode preview
- **Multiple audio tracks** and subtitle language selection

### 📺 Netflix-Style TV Interface
- **Auto-sliding hero section** with crossfade animations and 10-second intervals
- **Staggered content animations** with fade-in effects for professional polish
- **Smooth D-pad navigation** between all UI elements with proper focus management
- **48dp side navigation** with Netflix-red selection indicators and scale animations
- **Continue Watching** row with progress bars and resume functionality
- **Multiple content rows** (Trending, Popular, Latest, Action, Drama, Sci-Fi, Horror, Romance, Thriller)
- **Genre-based browsing** with paginated grid layouts and load more functionality
- **Search functionality** with virtual QWERTY keyboard and genre filtering
- **My List** screen for personalized content management
- **TV Series support** with season/episode navigation and detailed metadata
- **Recommendation system** with multiple algorithm endpoints (mixed, trending, popular, personalized)

### 🎮 TV Remote Optimization
- **Natural D-pad navigation** following Android TV guidelines
- **Focus management** with clear visual feedback
- **LEFT arrow** navigates to sidebar from any screen
- **BACK button** focuses navigation (Netflix behavior)
- **UP/DOWN arrows** for smooth content scrolling
- **No focus traps** - can navigate freely between areas
- **Auto-focus** on first content row at app launch

### 🏗️ Technical Architecture
- **Clean Architecture** with MVVM pattern and clear separation of concerns (domain, data, presentation)
- **Jetpack Compose** for modern declarative UI with Material Design 3 theming
- **Hilt dependency injection** for maintainable and testable code structure
- **ExoPlayer 3** integration with custom controls and subtitle rendering
- **Coroutines and Flow** for reactive programming and async operations
- **Navigation Component** with type-safe screen routing and deep linking
- **StateFlow** for reactive UI state management and lifecycle awareness
- **Coil** for efficient image loading with multi-tier caching (memory + disk)
- **Retrofit** for REST API communication with GSON serialization
- **SharedPreferences** for lightweight content caching (24-hour expiration)
- **Room Database** ready for advanced offline storage (planned)
- **Retry Policy** with exponential backoff for network resilience

## 🚀 Quick Start

### Prerequisites
- Android Studio Arctic Fox or later
- Android TV device or emulator (API 23+)
- **HomeFlix media server** running on your network
- HomeFlix backend setup from [HomeFlix Web App](https://github.com/azad25/homeflix-wifi)

### Installation

1. **Set up HomeFlix Backend**
   - Install and configure [HomeFlix Web App](https://github.com/azad25/homeflix-wifi)
   - Ensure your media server is running and accessible
   - Configure torrent download settings if desired

2. **Clone the TV client repository**
   ```bash
   git clone https://github.com/your-username/HomeFlixTV.git
   cd HomeFlixTV
   ```

3. **Configure backend connection**
   
   Update the base URL in `VideoPlayer.kt`:
   ```kotlin
   private fun getBaseUrl(): String {
       return "http://YOUR_SERVER_IP:8252"  // Replace with your server IP
   }
   ```
   
   Or update the API configuration in your network module for global configuration.

4. **Build and run**
   ```bash
   ./gradlew assembleDebug
   ```

   Or open in Android Studio and click **Run** ▶️

## 🚀 Version 2.0 - What's New

### ✨ Major Features
- **Intelligent Caching System** - 24-hour content cache for instant app startup with background refresh
- **TV Series Support** - Full season/episode navigation with autoplay and detailed metadata
- **My List Functionality** - Personalized watchlist management with add/remove capabilities
- **Enhanced Continue Watching** - Progress tracking with resume from any device and time-ago display
- **Smart Recommendations** - Multiple algorithm endpoints (mixed, trending, popular, personalized) with cycling system
- **Paginated Browse** - Memory-efficient browsing with load more functionality (24 items per page, max 200 in memory)

### 🎨 UI/UX Improvements
- **Staggered Animations** - Professional content reveal with fade-in effects and delayed entrance
- **Improved Focus Management** - Netflix-level D-pad navigation with no focus traps and proper sidebar exit
- **Enhanced Hero Section** - Crossfade transitions with 10-second auto-slide and staggered content animations
- **Better Loading States** - Smooth transitions with Netflix-red spinners and full-screen overlays
- **Refined Card Designs** - Border-only focus indicators (2dp white) with subtle scale animations (1.05x)
- **TV Series UI** - Season/episode cards with backdrop thumbnails and episode metadata

### ⚡ Performance Enhancements
- **Aggressive Image Caching** - 25% memory cache (256MB) + 512MB disk cache with Coil
- **Content Caching** - Instant startup with 24-hour cached content using SharedPreferences
- **Retry Policy** - Exponential backoff (3 attempts, 1s-10s delays) for network resilience
- **Network Monitoring** - LAN-optimized connectivity checks (WiFi/Ethernet, no internet required)
- **Reduced Logging** - Production-ready with minimal overhead (removed 50+ debug logs)
- **Memory Management** - Paginated loading with max 200 items to prevent OOM on budget devices

### 🐛 Bug Fixes
- Fixed subtitle loading blocking video playback (timeout + optional subtitle config)
- Resolved focus management issues in hero section (removed auto-focus interference)
- Fixed progress tracking sync across screens (unified API endpoint)
- Improved error handling for network failures (detailed error messages with server URL)
- Fixed memory leaks in image loading (proper lifecycle management)
- Fixed continue watching showing episodes on movie homepage (type filtering)
- Fixed hero slider showing old content (always show latest by creation date)

### 📚 Documentation
- Added comprehensive ENHANCEMENTS.md with caching and retry policy details
- Added PERFORMANCE_OPTIMIZATION.md with device-specific recommendations
- Updated LAN_SETUP_GUIDE.md with troubleshooting and network requirements
- Enhanced README with v2.0 features and accurate technical details

### 🔧 Technical Improvements
- **API Integration** - Full REST API with 30+ endpoints for media, playback, recommendations
- **State Management** - Improved StateFlow usage with proper lifecycle handling
- **Error Handling** - Comprehensive try-catch blocks with fallback mechanisms
- **Code Organization** - Clean separation of concerns with domain/data/presentation layers
- **Dependency Injection** - Hilt integration for all ViewModels and repositories

### 📋 Planned Features (v2.1+)
- Room Database integration for advanced offline storage and complex queries
- Background sync with WorkManager for periodic content updates
- Predictive caching for likely-to-watch content based on viewing patterns
- Advanced error recovery with circuit breaker pattern
- User profiles and preferences with personalized settings
- Cast integration for multi-device streaming (Chromecast support)
- Adaptive image cache based on device RAM (10%-25% scaling)
- Download support for offline viewing

---

## 🏗️ Architecture Overview

### Package Structure
```
com.homeflix.tv/
├── HomeFlixTVApplication.kt     # Application class with Hilt setup
├── di/                          # Dependency injection modules
│   └── NetworkModule.kt        # Retrofit and API configuration
├── domain/                      # Business logic and models
│   └── model/
│       ├── Media.kt            # Core media data model
│       ├── Genre.kt            # Genre classification
│       ├── ContinueWatching.kt # Playback progress tracking
│       └── MediaType.kt        # Movie/TV show enumeration
├── data/                        # Data access layer
│   ├── remote/api/             # API service interfaces
│   ├── remote/dto/             # Data transfer objects
│   └── repository/              # Repository implementations
├── presentation/                # UI layer (Jetpack Compose)
│   ├── MainActivity.kt         # Main TV activity
│   ├── navigation/             # Navigation graph and routes
│   ├── screens/                # Screen composables
│   │   ├── home/               # Home screen with hero section
│   │   ├── search/             # Search with virtual keyboard
│   │   ├── browse/             # Browse movies grid
│   │   ├── details/            # Movie details screen
│   │   └── player/             # Video player screen
│   ├── components/             # Reusable UI components
│   │   ├── NetflixHeroSection.kt    # Auto-sliding hero carousel
│   │   ├── NetflixSideNavigation.kt # 48dp sidebar navigation
│   │   ├── NetflixMediaCard.kt      # Focusable movie cards
│   │   ├── MediaRow.kt              # Horizontal content rows
│   │   └── VideoPlayer.kt           # ExoPlayer integration
│   └── theme/                  # Material Design 3 theming
└── util/                       # Utility classes and helpers
    └── ApiUtils.kt             # URL construction helpers
```

### Key Components

#### Video Player (`VideoPlayer.kt`)
- **Professional ExoPlayer integration** with custom controls
- **Netflix-style UI** with red accent colors and smooth animations
- **TV remote optimization** with D-pad navigation support
- **Auto-hiding controls** after 3 seconds of inactivity
- **Progress tracking** with automatic save on player exit
- **Enhanced subtitle support** with customizable text size and transparent background
- **Multiple playback speeds** and seeking controls
- **Volume control** with visual feedback

#### Hero Section (`NetflixHeroSection.kt`)
- **Auto-sliding carousel** with 5-second intervals
- **Staggered animations** for title, metadata, description, and buttons
- **Loading states** with Netflix-red spinner and fade transitions
- **Background crossfade** between different media items
- **Netflix-style metadata** with match percentage, year, rating, and genres
- **Responsive layout** optimized for 480dp height
- **Focus management** without blocking D-pad navigation

#### Side Navigation (`NetflixSideNavigation.kt`)
- **48dp wide icon-only sidebar** for clean TV interface
- **Individual focusable icons** with proper focus management
- **Netflix red selection** indicator for current page
- **White border focus** indicators with smooth transitions
- **Passive focus behavior** - only gets focus when explicitly requested
- **RIGHT arrow exit** back to content areas

#### Media Cards (`NetflixMediaCard.kt`)
- **Netflix-style scaling** animation on focus (1.05x scale)
- **White border indicators** with 3dp width for clear focus feedback
- **Smooth transitions** with 200ms animation timing
- **Aspect ratio optimization** (2:3) for poster display
- **Elevation changes** on focus for depth perception
- **Click handling** with proper navigation to details screens

## 🎮 Navigation System

### Netflix-Level D-Pad Navigation
The app implements a sophisticated navigation system that matches Netflix's TV app behavior:

```
App Launch Flow:
├── Focus: First content row (Continue Watching/Trending)
├── UP arrow: Navigate to hero section
├── DOWN arrow: Navigate between content rows
├── LEFT arrow: Navigate to sidebar from any screen
├── RIGHT arrow: Navigate back to content from sidebar
└── BACK button: Focus navigation (Netflix behavior)

Content Navigation:
├── Individual cards: Independently focusable with scaling animation
├── Smooth scrolling: Auto-scroll to focused items in rows
├── Visual feedback: White borders and scaling on focus
├── No focus traps: Can navigate freely between all areas
└── Natural traversal: System handles focus movement between elements
```

### Screen-Specific Features
- **Home Screen**: Auto-sliding hero, multiple content rows, continue watching
- **Search Screen**: Virtual keyboard, genre browsing, search results grid
- **Browse Screen**: Paginated movie grid with load more functionality
- **Details Screen**: Full movie information with play/info buttons
- **Video Player**: Custom controls with subtitle support and progress tracking

## 🎨 UI/UX Design

### Netflix-Inspired Interface
- **Material Design 3** with dark theme optimized for TV viewing
- **Netflix color scheme** with red accents (#E50914) and white text
- **Staggered animations** for professional content entrance
- **Smooth transitions** between all screens and states
- **Consistent focus indicators** across all interactive elements

### TV-Specific Optimizations
- **Landscape-only orientation** for TV viewing
- **48dp sidebar navigation** for easy thumb navigation
- **Large card layouts** (160dp width) optimized for viewing distance
- **High contrast colors** for visibility in various lighting conditions
- **Smooth scaling animations** for focus feedback

## 🔧 Configuration

### Backend Integration
HomeFlix TV connects to the HomeFlix web backend which provides:
- **Media library management** with automatic TMDB metadata fetching
- **Torrent download system** with real-time progress tracking and speed monitoring
- **Multi-source search** through Jackett integration (600+ torrent sources)
- **Quality filtering** (4K, 1080p, 720p, 480p) with automatic selection
- **Automatic transcoding** for unsupported formats with hardware acceleration
- **Subtitle management** with multiple language support and streaming
- **Playback progress tracking** with cross-device sync
- **Recommendation engine** with multiple algorithms (trending, popular, personalized)
- **My List management** with add/remove functionality
- **TV series hierarchy** with season/episode organization

### Network Configuration
```
Default Port: 8252
Protocol: HTTP (LAN only - no internet required)
Streaming: Direct file streaming with sendfile optimization
Transcoding: On-demand for unsupported formats (MKV → MP4)
Torrent Integration: Jackett with 600+ sources
API Endpoints: RESTful API with JSON responses
Caching: Multi-tier (L1/L2/L3) for sub-millisecond response
```

### Supported Media Formats
- **Video**: MP4, MKV, AVI, MOV, WMV, WebM
- **Audio**: AAC, MP3, AC3, DTS, ALAC (lossless), Opus
- **Subtitles**: SRT, VTT, ASS/SSA with customizable styling
- **Codecs**: H.264, H.265/HEVC, VP9, AV1
- **Containers**: Support for all major formats with automatic transcoding
- **Quality**: Up to 4K UHD with HDR support (hardware dependent)
- **Audio Tracks**: Multiple audio tracks with language selection
- **Subtitle Tracks**: Multiple subtitle tracks with language selection

## 📱 Development

### Building from Source
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Generate APK
./gradlew bundleDebug
```

### Code Style
- Follows [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Uses [Detekt](https://detekt.github.io/detekt/) for static analysis (planned)
- Implements [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html) principles
- MVVM pattern with StateFlow for reactive UI
- Dependency injection with Hilt
- Coroutines for async operations

### Testing
- Unit tests for ViewModels and repositories (planned)
- UI tests for critical user flows (planned)
- Integration tests for API communication (planned)
- Manual testing on Android TV devices

## 🚀 Current Development Status

### ✅ Completed Features
- **Netflix-style UI/UX** with Material Design 3
- **Smooth D-pad navigation** optimized for TV remotes
- **Auto-sliding hero section** with fade animations
- **Professional video player** with ExoPlayer integration
- **Enhanced subtitle support** with customizable styling
- **Focus management system** following Android TV guidelines
- **Multiple screen navigation** (Home, Search, Browse, Details, Player)
- **Progress tracking** with resume functionality
- **Search functionality** with virtual keyboard
- **Genre-based browsing** with paginated results

### 🔄 In Progress
- Backend integration improvements
- Additional streaming format support
- Performance optimizations
- Enhanced error handling

### 📋 Planned Features
- Watchlist functionality
- User profiles and preferences
- Advanced search filters
- Offline download support
- Cast integration

## 🐛 Troubleshooting

### Navigation Issues
- **Focus stuck**: Press BACK button to reset focus to navigation
- **Can't navigate**: Ensure D-pad is working, try LEFT arrow to access sidebar
- **Scroll issues**: Use UP/DOWN arrows, avoid using trackpad/mouse

### Video Playback Issues
- **No video**: Check server IP configuration in `getBaseUrl()` function
- **Buffering**: Verify network connection and server performance
- **Subtitles**: Press 'S' key or use subtitle button in player controls
- **Audio issues**: Check volume settings and audio codec compatibility

### Build Issues
- **Compilation errors**: Update Android Studio and sync Gradle
- **Dependencies**: Run `./gradlew clean build` to refresh dependencies
- **Focus issues**: Ensure target SDK is set to Android TV (API 23+)

## ©️ Copyright

**© 2025 Homeflix Studios. All Rights Reserved.**

Homeflix Studios is the creator and maintainer of the HomeFlix streaming platform ecosystem, including the HomeFlix web backend and Android TV applications.

## 🙏 Acknowledgments

- **ExoPlayer** team for professional-grade media playback library
- **Jetpack Compose** team for modern declarative UI toolkit
- **Android TV** team for TV platform guidelines and support
- **Material Design** team for comprehensive design system
- **Hilt** team for dependency injection framework
- **Coil** team for efficient image loading library
- **Netflix** for UI/UX inspiration and TV navigation patterns
- **HomeFlix** ecosystem for streaming backend integration

## 📞 Support

For issues, questions, or contributions:
- Open an issue on GitHub
- Check existing documentation
- Review troubleshooting section

🔗 **Backend Support**: [HomeFlix Web App Issues](https://github.com/azad25/homeflix-wifi/issues)

---