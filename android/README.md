# WhatsApp Digest - Android App

Silent WhatsApp-to-LLM Digest collector for Android devices.

## Overview

This Android app runs silently in the background, capturing WhatsApp notification content and sending it to your backend server for AI summarization. It's designed to be completely transparent to the user while providing valuable message insights.

## Features

- **Silent Operation**: Runs in background without disturbing your WhatsApp experience
- **Secure Storage**: Uses SQLCipher encryption for local message storage
- **Hourly Sync**: Automatically uploads collected messages to your server
- **Wi-Fi Only Option**: Optionally sync only when connected to Wi-Fi
- **Configuration UI**: Simple interface for setup and monitoring
- **Privacy First**: No data leaves your control - everything goes to your own server

## Requirements

- Android 8.0 (API 26) or higher
- Notification access permission
- WhatsApp installed and configured
- Backend server for message processing

## Installation

1. Download the APK from the releases page
2. Enable "Install from unknown sources" in Android settings
3. Install the APK
4. Open the app and grant notification access permission
5. Configure your server URL and device token

## Configuration

### Server Setup
- **Server URL**: Your backend API endpoint (e.g., `https://your-server.com/api`)
- **Device Token**: Authentication token for your device (manually entered)

### Sync Settings
- **Sync Interval**: How often to upload data (15-1440 minutes, default: 60)
- **Wi-Fi Only**: Enable to sync only when connected to Wi-Fi

## How It Works

1. **Notification Listening**: Uses Android's NotificationListenerService to capture WhatsApp notifications
2. **Local Storage**: Encrypts and stores message data locally using SQLCipher
3. **Background Sync**: WorkManager uploads data to your server every hour
4. **Data Cleanup**: Successfully uploaded messages are deleted immediately from local storage

## Privacy & Security

- **End-to-End Control**: All data goes to your own server
- **Local Encryption**: Database encrypted with device-specific keys
- **No Third Parties**: No data shared with external services
- **Minimal Permissions**: Only requests notification access
- **WhatsApp Compliant**: Uses public notification APIs only

## Permissions

- `BIND_NOTIFICATION_LISTENER_SERVICE`: Required to read WhatsApp notifications
- `INTERNET`: Required to upload data to your server
- `ACCESS_NETWORK_STATE`: Required to check network connectivity
- `WAKE_LOCK`: Required for background sync operations
- `RECEIVE_BOOT_COMPLETED`: Required to restart sync after device reboot

## Data Structure

Messages are captured with the following fields:
```json
{
  "chatId": "group_family_chat",
  "sender": "John Doe",
  "body": "Hey everyone, how's it going?",
  "timestamp": 1640995200000,
  "packageName": "com.whatsapp"
}
```

## Troubleshooting

### App Not Collecting Messages
1. Ensure notification access is granted
2. Check that WhatsApp notifications are enabled
3. Keep WhatsApp chats unmuted (but notifications can be silent)
4. Verify the app isn't being killed by battery optimization

### Sync Issues
1. Check server URL and device token configuration
2. Verify network connectivity
3. Check if Wi-Fi only mode is enabled when on mobile data
4. Review error messages in the app status section

### Battery Optimization
Add the app to battery optimization whitelist:
1. Go to Settings → Apps → WhatsApp Digest
2. Battery → Battery Optimization
3. Select "Don't optimize"

## Building from Source

```bash
git clone <repository-url>
cd whatsup/android
./gradlew assembleDebug
```

## Architecture

- **Kotlin**: Primary development language
- **Room + SQLCipher**: Encrypted local database
- **WorkManager**: Background sync scheduling
- **Retrofit**: HTTP client for API communication
- **Material 3**: UI design system
- **EncryptedSharedPreferences**: Secure configuration storage

## License

This project is for personal use only. Ensure compliance with WhatsApp's terms of service and local privacy laws.

## Support

This is a personal project. For issues:
1. Check the troubleshooting section above
2. Review app logs and error messages
3. Ensure your backend server is properly configured

## Disclaimer

This app reads WhatsApp notifications for personal use only. Users are responsible for:
- Complying with WhatsApp's terms of service
- Following local privacy and data protection laws
- Securing their own backend infrastructure
- Obtaining necessary consents for message processing

The app does not:
- Send messages back to WhatsApp
- Access WhatsApp's internal APIs
- Share data with third parties
- Store messages permanently (deleted after successful upload)