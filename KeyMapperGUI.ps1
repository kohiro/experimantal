$code = @"
using System;
using System.Drawing;
using System.Windows.Forms;
using System.Runtime.InteropServices;
using System.Threading;

namespace CustomKeyMapper
{
    public class MainForm : Form
    {
        [DllImport("user32.dll")]
        public static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);
        [DllImport("user32.dll")]
        public static extern bool UnregisterHotKey(IntPtr hWnd, int id);
        [DllImport("user32.dll")]
        static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, int dwExtraInfo);

        // Modifiers for RegisterHotKey
        const uint MOD_ALT = 0x0001;
        const uint MOD_CONTROL = 0x0002;
        const uint MOD_SHIFT = 0x0004;
        const uint MOD_WIN = 0x0008;
        const uint MOD_NOREPEAT = 0x4000;
        const int HOTKEY_ID = 9000;

        // UI Controls
        GroupBox grpTrigger, grpAction;
        CheckBox chkTrigCtrl, chkTrigAlt, chkTrigShift, chkTrigWin;
        ComboBox cmbTrigKey;
        CheckBox chkActCtrl, chkActAlt, chkActShift, chkActWin;
        ComboBox cmbActKey;
        Button btnStart, btnStop;
        Label lblStatus;

        bool isRunning = false;

        public MainForm()
        {
            this.Text = "Custom Key Mapper";
            this.Size = new Size(340, 320);
            this.FormBorderStyle = FormBorderStyle.FixedDialog;
            this.MaximizeBox = false;
            this.StartPosition = FormStartPosition.CenterScreen;

            // --- Trigger Section ---
            grpTrigger = new GroupBox() { Text = "1. トリガー (入力キー)", Bounds = new Rectangle(10, 10, 300, 80) };
            chkTrigCtrl = new CheckBox() { Text = "Ctrl", Bounds = new Rectangle(10, 20, 50, 20) };
            chkTrigAlt = new CheckBox() { Text = "Alt", Bounds = new Rectangle(60, 20, 50, 20) };
            chkTrigShift = new CheckBox() { Text = "Shift", Bounds = new Rectangle(110, 20, 60, 20), Checked = true };
            chkTrigWin = new CheckBox() { Text = "Win", Bounds = new Rectangle(170, 20, 50, 20) };
            cmbTrigKey = new ComboBox() { Bounds = new Rectangle(10, 45, 120, 20), DropDownStyle = ComboBoxStyle.DropDownList };
            
            grpTrigger.Controls.AddRange(new Control[] { chkTrigCtrl, chkTrigAlt, chkTrigShift, chkTrigWin, cmbTrigKey });

            // --- Action Section ---
            grpAction = new GroupBox() { Text = "2. アクション (変換先)", Bounds = new Rectangle(10, 100, 300, 80) };
            chkActCtrl = new CheckBox() { Text = "Ctrl", Bounds = new Rectangle(10, 20, 50, 20) };
            chkActAlt = new CheckBox() { Text = "Alt", Bounds = new Rectangle(60, 20, 50, 20) };
            chkActShift = new CheckBox() { Text = "Shift", Bounds = new Rectangle(110, 20, 60, 20) };
            chkActWin = new CheckBox() { Text = "Win", Bounds = new Rectangle(170, 20, 50, 20), Checked = true };
            cmbActKey = new ComboBox() { Bounds = new Rectangle(10, 45, 120, 20), DropDownStyle = ComboBoxStyle.DropDownList };
            
            grpAction.Controls.AddRange(new Control[] { chkActCtrl, chkActAlt, chkActShift, chkActWin, cmbActKey });

            // --- Buttons & Status ---
            btnStart = new Button() { Text = "マッピング開始", Bounds = new Rectangle(10, 190, 140, 30) };
            btnStart.Click += BtnStart_Click;
            btnStop = new Button() { Text = "停止", Bounds = new Rectangle(170, 190, 140, 30), Enabled = false };
            btnStop.Click += BtnStop_Click;
            lblStatus = new Label() { Text = "ステータス: 停止中", Bounds = new Rectangle(10, 230, 300, 20), ForeColor = Color.Red };

            this.Controls.AddRange(new Control[] { grpTrigger, grpAction, btnStart, btnStop, lblStatus });

            // Initialize Dropdowns
            InitializeKeys();
        }

        private void InitializeKeys()
        {
            // 主要なキーをドロップダウンに登録
            var keys = new[] { 
                Keys.Insert, Keys.Delete, Keys.Home, Keys.End, Keys.PageUp, Keys.PageDown,
                Keys.A, Keys.B, Keys.C, Keys.D, Keys.E, Keys.F, Keys.G, Keys.H, Keys.I, Keys.J, 
                Keys.K, Keys.L, Keys.M, Keys.N, Keys.O, Keys.P, Keys.Q, Keys.R, Keys.S, Keys.T, 
                Keys.U, Keys.V, Keys.W, Keys.X, Keys.Y, Keys.Z,
                Keys.Enter, Keys.Space, Keys.Tab, Keys.Escape
            };
            foreach(var k in keys) {
                cmbTrigKey.Items.Add(k);
                cmbActKey.Items.Add(k);
            }
            // 初期値を Shift + Insert -> Win + V にセット
            cmbTrigKey.SelectedItem = Keys.Insert;
            cmbActKey.SelectedItem = Keys.V;
        }

        private void BtnStart_Click(object sender, EventArgs e)
        {
            uint fsModifiers = MOD_NOREPEAT;
            if (chkTrigAlt.Checked) fsModifiers |= MOD_ALT;
            if (chkTrigCtrl.Checked) fsModifiers |= MOD_CONTROL;
            if (chkTrigShift.Checked) fsModifiers |= MOD_SHIFT;
            if (chkTrigWin.Checked) fsModifiers |= MOD_WIN;

            uint vk = (uint)(Keys)cmbTrigKey.SelectedItem;

            if (RegisterHotKey(this.Handle, HOTKEY_ID, fsModifiers, vk))
            {
                lblStatus.Text = "ステータス: 実行中 (システムトレイに最小化できます)";
                lblStatus.ForeColor = Color.Green;
                btnStart.Enabled = false;
                btnStop.Enabled = true;
                ToggleUI(false);
                isRunning = true;
            }
            else
            {
                MessageBox.Show("他のアプリがこのキーを使用中か、登録に失敗しました。", "エラー", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void BtnStop_Click(object sender, EventArgs e)
        {
            UnregisterHotKey(this.Handle, HOTKEY_ID);
            lblStatus.Text = "ステータス: 停止中";
            lblStatus.ForeColor = Color.Red;
            btnStart.Enabled = true;
            btnStop.Enabled = false;
            ToggleUI(true);
            isRunning = false;
        }

        private void ToggleUI(bool enable)
        {
            grpTrigger.Enabled = enable;
            grpAction.Enabled = enable;
        }

        // WM_HOTKEY (0x0312) メッセージをキャッチして処理
        protected override void WndProc(ref Message m)
        {
            if (m.Msg == 0x0312 && m.WParam.ToInt32() == HOTKEY_ID)
            {
                ExecuteAction();
            }
            base.WndProc(ref m);
        }

        private void ExecuteAction()
        {
            // 1. トリガーキーの物理状態を論理的に解放 (競合防止)
            if (chkTrigCtrl.Checked) keybd_event(0x11, 0, 0x0002, 0);
            if (chkTrigAlt.Checked) keybd_event(0x12, 0, 0x0002, 0);
            if (chkTrigShift.Checked) keybd_event(0x10, 0, 0x0002, 0);
            if (chkTrigWin.Checked) keybd_event(0x5B, 0, 0x0002, 0);

            Thread.Sleep(50); // OSの認識待ち

            // 2. アクションキーを送信
            byte actVk = (byte)(Keys)cmbActKey.SelectedItem;
            if (chkActCtrl.Checked) keybd_event(0x11, 0, 0, 0);
            if (chkActAlt.Checked) keybd_event(0x12, 0, 0, 0);
            if (chkActShift.Checked) keybd_event(0x10, 0, 0, 0);
            if (chkActWin.Checked) keybd_event(0x5B, 0, 0, 0);

            keybd_event(actVk, 0, 0, 0);
            keybd_event(actVk, 0, 0x0002, 0);

            if (chkActWin.Checked) keybd_event(0x5B, 0, 0x0002, 0);
            if (chkActShift.Checked) keybd_event(0x10, 0, 0x0002, 0);
            if (chkActAlt.Checked) keybd_event(0x12, 0, 0x0002, 0);
            if (chkActCtrl.Checked) keybd_event(0x11, 0, 0x0002, 0);
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            if (isRunning) UnregisterHotKey(this.Handle, HOTKEY_ID);
            base.OnFormClosing(e);
        }
    }

    static class Program
    {
        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }
}
"@

# アセンブリとしてコンパイル (System.Windows.Formsを追加)
Add-Type -TypeDefinition $code -ReferencedAssemblies "System.Windows.Forms", "System.Drawing" -OutputAssembly "$HOME\Documents\KeyMapperGUI.exe" -OutputType WindowsApplication
