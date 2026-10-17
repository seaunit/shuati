package site.seaunit.shuati;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 参考效果是"系统栏占位、页面内容在它下面"：关掉 edge-to-edge，
        // 避免 WebView 铺到状态栏/导航栏底下，不同机型与系统版本表现一致。
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 真机上切后台再回来，系统栏会被重新刷成深色，直接把状态栏盖成黑条。
        // 光靠主题属性不可靠，这里每次回到前台都重新钉一遍：白底 + 深色图标。
        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
    }
}
