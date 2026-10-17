package site.seaunit.shuati;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Capacitor 是在 super.onCreate() 之后才 setTheme(正式主题) 的，那时窗口已经按
        // 清单里声明的启动主题（Theme.SplashScreen）建好了，窗口级属性（窗口底色、
        // 系统栏颜色）不会跟着换。启动主题的窗口底色是黑的——这就是：
        //   · 上下两条黑边（透明系统栏下面露出黑窗口）
        //   · 切后台回来整屏发黑（WebView 表面丢了，露出同一个黑窗口）
        // 所以窗口级的东西在这里显式设一遍，不再依赖主题属性。
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        // 参考效果是"系统栏占位、页面内容在它下面"，关掉 edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, true);
        applyLightSystemBars(window);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 有些机型回到前台会把系统栏刷回深色，这里每次重新钉一遍
        applyLightSystemBars(getWindow());
    }

    /** 系统栏铺白 + 深色图标（状态栏/导航栏都要，缺一个就有一条黑边） */
    private void applyLightSystemBars(Window window) {
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
    }
}
