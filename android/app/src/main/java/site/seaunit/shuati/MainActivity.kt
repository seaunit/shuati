package site.seaunit.shuati

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import site.seaunit.shuati.ui.AppNav
import site.seaunit.shuati.ui.theme.ShuatiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ShuatiApp).container
        setContent {
            ShuatiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav(container)
                }
            }
        }
    }
}
