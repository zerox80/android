package screens

import com.kaspersky.components.kautomator.component.edit.UiEditText
import com.kaspersky.components.kautomator.component.text.UiButton
import com.kaspersky.components.kautomator.screen.UiScreen

object LoginScreen : UiScreen<LoginScreen>() {
    override val packageName: String = "com.android.chrome"

    // can't find it using withId("com.android.chrome", "username") so using withResourceName()
    val username = UiEditText { withResourceName("username") }
    val password = UiEditText { withResourceName("password") }
    val loginButton = UiButton { withResourceName("kc-login") }
}
