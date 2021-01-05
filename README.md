# Seshat Calendar
Based on the mix of "Calendar Notifications Plus" and calendar by The Etar Project (based on AOSP), this app implements an alternative calendar app for android that takes an additional care to make sure calendar event notifications are delivered on time and ensures that such notifications are not lost by any accident - notification stays until you mark it as done. 


## What does the name mean? 
Seshat is the ancient Egyptian goddess of wisdom, knowledge, and writing. 

## Special thanks

The application is an enhanced version of The Etar Calendar, which itself is en enhanced version of AOSP Calendar.

### Technical explanation
On Android there are "Calendar providers". These can be calendars that are
synchronized with a cloud service or local calendars. Basically any app
could provide a calendar. Those "provided" calendars can be used by Seshat.
You can even configure in Seshat which ones are to be shown and when adding
an event to which calendar it should be added.

### Important permissions Seshat requires
- READ_EXTERNAL_STORAGE & WRITE_EXTERNAL_STORAGE  
->import and export ics calendar files  
- READ_CONTACTS(optional)  
  Is queried the first time an appointment is created and can be rejected. But then search and location suggestions no longer work.
->allows search and location suggestions when adding guests to an event  
- READ_CALENDAR & WRITE_CALENDAR  
->read and create calendar events

## Build instructions
Install and extract Android SDK command line tools.
```
tools/bin/sdkmanager platform-tools
export ANDROID_HOME=/path/to/android-sdk/
git submodule update --init
gradle build
```
## License

Copyright (c) 2005-2013, The Android Open Source Project

Copyright (c) 2013, Dominik Schürmann

Copyright (c) 2015-2020, The Etar Project

Copyright (c) 2020-2021, Sergey Parshin

Licensed under the GPLv3: https://www.gnu.org/licenses/gpl-3.0.html
Except where otherwise noted.

Google Play and the Google Play logo are trademarks of Google Inc.
