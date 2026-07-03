//! Helper for the global "quick translate" shortcut: synthesize Ctrl+C so the
//! user's current selection (in any app) lands on the clipboard, which we then
//! read and translate.

/// Send a Ctrl+C keystroke to the foreground window (Windows only).
#[cfg(windows)]
pub fn send_ctrl_c() {
    use windows::Win32::UI::Input::KeyboardAndMouse::{
        SendInput, INPUT, INPUT_0, INPUT_KEYBOARD, KEYBDINPUT, KEYBD_EVENT_FLAGS, KEYEVENTF_KEYUP,
        VIRTUAL_KEY, VK_CONTROL,
    };

    fn key(vk: u16, up: bool) -> INPUT {
        INPUT {
            r#type: INPUT_KEYBOARD,
            Anonymous: INPUT_0 {
                ki: KEYBDINPUT {
                    wVk: VIRTUAL_KEY(vk),
                    wScan: 0,
                    dwFlags: if up {
                        KEYEVENTF_KEYUP
                    } else {
                        KEYBD_EVENT_FLAGS(0)
                    },
                    time: 0,
                    dwExtraInfo: 0,
                },
            },
        }
    }

    const VK_C: u16 = 0x43;
    let inputs = [
        key(VK_CONTROL.0, false),
        key(VK_C, false),
        key(VK_C, true),
        key(VK_CONTROL.0, true),
    ];
    unsafe {
        SendInput(&inputs, std::mem::size_of::<INPUT>() as i32);
    }
}

#[cfg(not(windows))]
pub fn send_ctrl_c() {}
