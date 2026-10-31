//go:build windows

// A small GUI launcher for the portable Windows package. No external Go modules are used.
package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"syscall"
	"unsafe"
)

func fail(message string) {
	text, _ := syscall.UTF16PtrFromString(message)
	title, _ := syscall.UTF16PtrFromString("Quiz Arena")
	syscall.NewLazyDLL("user32.dll").NewProc("MessageBoxW").Call(
		0, uintptr(unsafe.Pointer(text)), uintptr(unsafe.Pointer(title)), 0x10)
	os.Exit(1)
}

func main() {
	launcher, err := os.Executable()
	if err != nil {
		fail("Không xác định được thư mục ứng dụng.")
	}
	root := filepath.Dir(launcher)
	java := filepath.Join(root, "runtime", "bin", "javaw.exe")
	if _, err = os.Stat(java); err != nil {
		fail("Thiếu Java runtime. Hãy giải nén toàn bộ file ZIP trước khi mở Quiz Arena.exe.")
	}
	logDir := filepath.Join(os.Getenv("LOCALAPPDATA"), "Quiz Arena", "logs")
	if err = os.MkdirAll(logDir, 0755); err != nil {
		fail("Không tạo được thư mục nhật ký: " + err.Error())
	}
	logPath := filepath.Join(logDir, "client.log")
	log, err := os.OpenFile(logPath, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0600)
	if err != nil {
		fail("Không ghi được nhật ký: " + err.Error())
	}
	defer log.Close()
	args := []string{"-cp", filepath.Join(root, "lib", "*"), "vn.edu.nhom7.quiz.client.DesktopLauncher"}
	args = append(args, os.Args[1:]...)
	app := exec.Command(java, args...)
	app.Dir = root
	app.Stdout, app.Stderr = log, log
	app.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	if err = app.Run(); err != nil {
		fail(fmt.Sprintf("Không mở được Quiz Arena.\n%s\nNhật ký: %s", err, logPath))
	}
}
