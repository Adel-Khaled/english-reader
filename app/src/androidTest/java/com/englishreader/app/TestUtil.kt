package com.englishreader.app

import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream

/** Runs a shell command as the shell user and waits for it to finish. */
fun shell(cmd: String): String {
    val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
    return FileInputStream(pfd.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }.also { pfd.close() }
}

/** Saves a screenshot to /data/local/tmp/screens (pulled by CI after the run). */
fun screenshot(name: String) {
    shell("mkdir -p /data/local/tmp/screens")
    shell("screencap -p /data/local/tmp/screens/$name.png")
}

fun container(): AppContainer =
    (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as EnglishReaderApp).container

private val device get() = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

/** System back press that does not depend on window focus (robust on slow CI emulators). */
fun pressBack() {
    dismissSystemDialogs()
    device.pressBack()
    Thread.sleep(700)
}

/** Closes "System UI isn't responding" style dialogs that slow emulators sometimes show. */
fun dismissSystemDialogs() {
    for (label in listOf("Wait", "Close app", "OK")) {
        device.findObject(androidx.test.uiautomator.By.text(label))?.let { if (label == "Wait") it.click() }
    }
}
