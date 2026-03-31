package eu.opencloud.android

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.rule.GrantPermissionRule
import com.kaspersky.kaspresso.kaspresso.Kaspresso
import com.kaspersky.kaspresso.params.FlakySafetyParams
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import eu.opencloud.android.ui.activity.SplashActivity
import org.junit.Rule
import org.junit.Test
import screens.LoginScreen
import screens.MainScreen
import screens.ManageAccountsDialog
import screens.StartScreen

class LoginScreenTest : TestCase(
    kaspressoBuilder = Kaspresso.Builder.advanced {
        flakySafetyParams = FlakySafetyParams.custom(
            timeoutMs = 20_000L,
            intervalMs = 500L
        )
    }
) {
    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    @get:Rule
    val activityRule = ActivityScenarioRule(SplashActivity::class.java)


    @Test
    fun loginApp() {
        before {
        }.after {
            adbServer.performCmd("adb", listOf("shell", "am", "force-stop", "com.android.chrome"))
        }.run {
            step("set opencloud url") {
                StartScreen {
                    hostUrlInput {
                        isVisible()
                        typeText("https://demo.opencloud.eu")
                    }
                    checkServerButton {
                        isVisible()
                        isClickable()
                        click()
                    }
                }
            }
            step("wait chrome login page") {
                flakySafely(timeoutMs = 10_000) {
                    LoginScreen {
                        loginButton.isDisplayed()
                    }
                }
            }
            step("login") {
                LoginScreen {
                    username.typeText("alan")
                    password.typeText("demo")
                    loginButton.click()
                }
            }
            step("check personal space") {
                MainScreen {
                    avatarButton.isVisible()
                    avatarButton.isClickable()
                    avatarButton.click()
                }
            }
            step("remove account") {
                ManageAccountsDialog {
                    removeBtn {
                        isVisible()
                        click()
                    }
                    message.isVisible()
                    confirmBtn.click()
                }
            }
        }
    }
}
