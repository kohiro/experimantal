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

        const uint MOD_ALT = 0x0001;
        const uint MOD_CONTROL = 0x0002;
        const uint MOD_SHIFT = 0x0004;
        const uint MOD_WIN = 0x0008;
        const uint MOD_NOREPEAT = 0x4000;
        const int HOTKEY_ID = 9000;

        GroupBox grpTrigger, grpAction;
        CheckBox chkTrigCtrl, chkTrigAlt, chkTrigShift, chkTrigWin;
        ComboBox cmbTrigKey;
        CheckBox chkActCtrl, chkActAlt, chkActShift, chkActWin;
        ComboBox cmbActKey;
        Button btnStart, btnStop;
        Label lblStatus;
        
        NotifyIcon trayIcon;
        
        bool isRunning = false;
        bool startHidden = false;

        public MainForm(string[] args)
        {
            foreach(string arg in args) {
                if(arg.ToLower() == "-hidden") startHidden = true;
            }

            this.Text = "Custom Key Mapper V5";
            this.Size = new Size(340, 320);
            this.FormBorderStyle = FormBorderStyle.FixedDialog;
            this.MaximizeBox = false;
            this.StartPosition = FormStartPosition.CenterScreen;

            // --- UI構築 ---
            grpTrigger = new GroupBox() { Text = "1. トリガー (入力キー)", Bounds = new Rectangle(10, 10, 300, 80) };
            chkTrigCtrl = new CheckBox() { Text = "Ctrl", Bounds = new Rectangle(10, 20, 50, 20) };
            chkTrigAlt = new CheckBox() { Text = "Alt", Bounds = new Rectangle(60, 20, 50, 20) };
            chkTrigShift = new CheckBox() { Text = "Shift", Bounds = new Rectangle(110, 20, 60, 20), Checked = true };
            chkTrigWin = new CheckBox() { Text = "Win", Bounds = new Rectangle(170, 20, 50, 20) };
            cmbTrigKey = new ComboBox() { Bounds = new Rectangle(10, 45, 120, 20), DropDownStyle = ComboBoxStyle.DropDownList };
            grpTrigger.Controls.AddRange(new Control[] { chkTrigCtrl, chkTrigAlt, chkTrigShift, chkTrigWin, cmbTrigKey });

            grpAction = new GroupBox() { Text = "2. アクション (変換先)", Bounds = new Rectangle(10, 100, 300, 80) };
            chkActCtrl = new CheckBox() { Text = "Ctrl", Bounds = new Rectangle(10, 20, 50, 20) };
            chkActAlt = new CheckBox() { Text = "Alt", Bounds = new Rectangle(60, 20, 50, 20) };
            chkActShift = new CheckBox() { Text = "Shift", Bounds = new Rectangle(110, 20, 60, 20) };
            chkActWin = new CheckBox() { Text = "Win", Bounds = new Rectangle(170, 20, 50, 20), Checked = true };
            cmbActKey = new ComboBox() { Bounds = new Rectangle(10, 45, 120, 20), DropDownStyle = ComboBoxStyle.DropDownList };
            grpAction.Controls.AddRange(new Control[] { chkActCtrl, chkActAlt, chkActShift, chkActWin, cmbActKey });

            btnStart = new Button() { Text = "マッピング開始", Bounds = new Rectangle(10, 190, 140, 30) };
            btnStart.Click += BtnStart_Click;
            btnStop = new Button() { Text = "停止", Bounds = new Rectangle(170, 190, 140, 30), Enabled = false };
            btnStop.Click += BtnStop_Click;
            lblStatus = new Label() { Text = "ステータス: 停止中", Bounds = new Rectangle(10, 230, 300, 20), ForeColor = Color.Red };

            this.Controls.AddRange(new Control[] { grpTrigger, grpAction, btnStart, btnStop, lblStatus });
            InitializeKeys();

            // --- タスクトレイの設定 ---
            trayIcon = new NotifyIcon();
            trayIcon.Text = "Key Mapper (稼働中)";
            trayIcon.Icon = SystemIcons.Information;
            trayIcon.DoubleClick += (s, e) => { ShowForm(); };

            ContextMenu trayMenu = new ContextMenu();
            trayMenu.MenuItems.Add("設定画面を開く", (s, e) => { ShowForm(); });
            trayMenu.MenuItems.Add("-");
            trayMenu.MenuItems.Add("アプリを完全終了", (s, e) => { 
                trayIcon.Visible = false;
                Application.Exit(); 
            });
            trayIcon.ContextMenu = trayMenu;

            this.FormClosing += (s, e) => {
                if (e.CloseReason == CloseReason.UserClosing) {
                    e.Cancel = true;
                    HideForm();
                }
            };

            this.Load += (s, e) => {
                if (startHidden) {
                    StartMapping();
                }
            };
        }

        protected override void SetVisibleCore(bool value)
        {
            if (startHidden && !this.IsHandleCreated) {
                CreateHandle();
                value = false;
            }
            base.SetVisibleCore(value);
        }

        private void InitializeKeys()
        {
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
            cmbTrigKey.SelectedItem = Keys.Insert;
            cmbActKey.SelectedItem = Keys.V;
        }

        private void BtnStart_Click(object sender, EventArgs e) { StartMapping(); }

        private void StartMapping()
        {
            if (isRunning) return;

            uint fsModifiers = MOD_NOREPEAT;
            if (chkTrigAlt.Checked) fsModifiers |= MOD_ALT;
            if (chkTrigCtrl.Checked) fsModifiers |= MOD_CONTROL;
            if (chkTrigShift.Checked) fsModifiers |= MOD_SHIFT;
            if (chkTrigWin.Checked) fsModifiers |= MOD_WIN;

            uint vk = (uint)(Keys)cmbTrigKey.SelectedItem;

            if (RegisterHotKey(this.Handle, HOTKEY_ID, fsModifiers, vk))
            {
                lblStatus.Text = "ステータス: 実行中";
                lblStatus.ForeColor = Color.Green;
                btnStart.Enabled = false;
                btnStop.Enabled = true;
                grpTrigger.Enabled = false;
                grpAction.Enabled = false;
                isRunning = true;
                trayIcon.Visible = true;

                if (startHidden) {
                    HideForm();
                    startHidden = false; 
                }
            }
        }

        private void BtnStop_Click(object sender, EventArgs e)
        {
            UnregisterHotKey(this.Handle, HOTKEY_ID);
            lblStatus.Text = "ステータス: 停止中";
            lblStatus.ForeColor = Color.Red;
            btnStart.Enabled = true;
            btnStop.Enabled = false;
            grpTrigger.Enabled = true;
            grpAction.Enabled = true;
            isRunning = false;
            trayIcon.Visible = false;
        }

        // --- 修正箇所：ハンドルの再生成を引き起こす ShowInTaskbar 操作を削除 ---
        private void ShowForm()
        {
            this.Show();
            this.WindowState = FormWindowState.Normal;
        }

        private void HideForm()
        {
            this.Hide(); 
        }
        // ------------------------------------------------------------------------

        protected override void WndProc(ref Message m)
        {
            if (m.Msg == 0x0312 && m.WParam.ToInt32() == HOTKEY_ID)
            {
                if (chkTrigCtrl.Checked) keybd_event(0x11, 0, 0x0002, 0);
                if (chkTrigAlt.Checked) keybd_event(0x12, 0, 0x0002, 0);
                if (chkTrigShift.Checked) keybd_event(0x10, 0, 0x0002, 0);
                if (chkTrigWin.Checked) keybd_event(0x5B, 0, 0x0002, 0);

                Thread.Sleep(50);

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
            base.WndProc(ref m);
        }
    }

    static class Program
    {
        [STAThread]
        static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm(args));
        }
    }
}
"@

Add-Type -TypeDefinition $code -ReferencedAssemblies "System.Windows.Forms", "System.Drawing" -OutputAssembly "$HOME\Documents\KeyMapperGUIV5.exe" -OutputType WindowsApplication
