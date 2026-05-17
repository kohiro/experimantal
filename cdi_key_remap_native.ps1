$code = @"
using System.Runtime.InteropServices;
class Program {
    [DllImport("user32.dll")]
    static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, int dwExtraInfo);
    static void Main() {
        // キーの同時押し競合を防ぐためのわずかな待機（0.2秒）
        System.Threading.Thread.Sleep(200); 
        keybd_event(0x5B, 0, 0, 0);         // Winキー押下
        keybd_event(0x56, 0, 0, 0);         // Vキー押下
        keybd_event(0x56, 0, 0x0002, 0);    // Vキー離す
        keybd_event(0x5B, 0, 0x0002, 0);    // Winキー離す
    }
}
"@
Add-Type -TypeDefinition $code -OutputAssembly "$HOME\Documents\OpenClip.exe" -OutputType WindowApplication
