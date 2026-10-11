// Package xraycore is WayTun's thin gomobile wrapper around Xray-core: start/stop from a JSON config,
// the Android TUN descriptor handed over through Xray's own `xray.tun.fd` mechanism, and a hook so the
// app can exclude Xray's own sockets from the VPN (VpnService.protect).
package xraycore

import (
	"errors"
	"os"
	"strconv"
	"sync"
	"syscall"

	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/transport/internet"

	// All of Xray's protocols/transports/features and the JSON config loader.
	_ "github.com/xtls/xray-core/main/distro/all"
)

// Protector is implemented by the app: Protect(fd) must call VpnService.protect(fd).
type Protector interface {
	Protect(fd int) bool
}

var (
	mu        sync.Mutex
	instance  *core.Instance
	protector Protector
	hooked    bool
)

// SetProtector installs the socket-protection callback. Call before Start.
func SetProtector(p Protector) error {
	mu.Lock()
	defer mu.Unlock()
	protector = p
	if hooked {
		return nil
	}
	hooked = true
	control := func(network, address string, c syscall.RawConn) error {
		mu.Lock()
		p := protector
		mu.Unlock()
		if p == nil {
			return nil
		}
		var ok bool
		if err := c.Control(func(fd uintptr) { ok = p.Protect(int(fd)) }); err != nil {
			return err
		}
		if !ok {
			return errors.New("VpnService.protect failed")
		}
		return nil
	}
	if err := internet.RegisterDialerController(control); err != nil {
		return err
	}
	return internet.RegisterListenerController(control)
}

// Start brings Xray up from a JSON config. tunFd > 0 is the VpnService TUN descriptor for the config's
// `tun` inbound; assetDir is where geoip/geosite .dat files would live (may be empty).
func Start(configJSON string, tunFd int, assetDir string) error {
	mu.Lock()
	defer mu.Unlock()
	if instance != nil {
		return errors.New("xray is already running")
	}
	if tunFd > 0 {
		_ = os.Setenv("XRAY_TUN_FD", strconv.Itoa(tunFd))
		_ = os.Setenv("xray.tun.fd", strconv.Itoa(tunFd))
	}
	if assetDir != "" {
		_ = os.Setenv("XRAY_LOCATION_ASSET", assetDir)
	}
	inst, err := core.StartInstance("json", []byte(configJSON))
	if err != nil {
		return err
	}
	instance = inst
	return nil
}

// Stop shuts the running instance down (no-op if none).
func Stop() error {
	mu.Lock()
	defer mu.Unlock()
	if instance == nil {
		return nil
	}
	err := instance.Close()
	instance = nil
	return err
}

// IsRunning reports whether an instance is up.
func IsRunning() bool {
	mu.Lock()
	defer mu.Unlock()
	return instance != nil
}

// Version is the Xray-core version string.
func Version() string { return core.Version() }
