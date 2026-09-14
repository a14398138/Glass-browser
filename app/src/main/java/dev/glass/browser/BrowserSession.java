package dev.glass.browser;
import android.os.Bundle;
import android.webkit.WebView;
/** In-process handoff avoids passing potentially large WebView history via Binder. */
final class BrowserSession {
    private static Bundle state;
    static void capture(WebView web){if(web.getUrl()==null||web.getUrl().startsWith("https://glass.invalid")){state=null;return;}Bundle b=new Bundle();web.saveState(b);state=b;}
    static Bundle take(){Bundle b=state;state=null;return b;}
}
