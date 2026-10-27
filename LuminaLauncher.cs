using System;
using System.Diagnostics;
using System.IO;

namespace LuminaStreamsLauncher
{
    static class Program
    {
        [STAThread]
        static void Main()
        {
            try
            {
                string localApp = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                string wsaExe = Path.Combine(localApp, @"Microsoft\WindowsApps\wsaclient.exe");

                var psi = new ProcessStartInfo
                {
                    FileName = wsaExe,
                    Arguments = "/launch wsa://com.luminastreams.tv",
                    UseShellExecute = true,
                    WindowStyle = ProcessWindowStyle.Hidden
                };
                Process.Start(psi);
            }
            catch
            {
                try
                {
                    var psiFallback = new ProcessStartInfo
                    {
                        FileName = "cmd.exe",
                        Arguments = "/c start \"\" wsaclient.exe /launch wsa://com.luminastreams.tv",
                        UseShellExecute = true,
                        WindowStyle = ProcessWindowStyle.Hidden
                    };
                    Process.Start(psiFallback);
                }
                catch { }
            }
        }
    }
}
