Forked from [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android)

## Changes

- Set default SwipeSymbolDirection to Up
- Change the position of swipe symbols to top-right
- Change the default binding of swipe symbols
- Remove the auto quitting logic of editor mode
- Add tab button and remove language button, and add long press event for tab
- Add swiping logic for ",/，" and "./。"

### Note:

To change the swipe symbol binding, modify:

1. `./app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/TextKeyboard.kt`
2. `./app/src/main/java/org/fcitx/fcitx5/android/input/popup/PopupPreset.kt`: Part `(Upper case)Latin` and bindings of `.` in Part `Punctuation`
