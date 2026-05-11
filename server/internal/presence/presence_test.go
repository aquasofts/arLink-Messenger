package presence

import (
	"testing"
	"time"
)

func TestConnectDisconnect(t *testing.T) {
	tr := New()
	tr.Connect("dev1")
	if !tr.IsOnline("dev1") {
		t.Fatal("expected online")
	}
	tr.Disconnect("dev1")
	if tr.IsOnline("dev1") {
		t.Fatal("expected offline")
	}
	_, ls := tr.Snapshot("dev1")
	if ls == 0 {
		t.Fatal("expected last_seen recorded")
	}
}

func TestSubscribeReceivesEvents(t *testing.T) {
	tr := New()
	ch := tr.Subscribe("subscriber", []string{"dev2"})
	// 立即推送 snapshot
	select {
	case ev := <-ch:
		if ev.DeviceID != "dev2" {
			t.Fatalf("unexpected device: %s", ev.DeviceID)
		}
	case <-time.After(time.Second):
		t.Fatal("snapshot not received")
	}

	tr.Connect("dev2")
	select {
	case ev := <-ch:
		if !ev.Online || ev.DeviceID != "dev2" {
			t.Fatalf("unexpected event: %+v", ev)
		}
	case <-time.After(time.Second):
		t.Fatal("connect event not received")
	}

	tr.Disconnect("dev2")
	select {
	case ev := <-ch:
		if ev.Online {
			t.Fatal("expected offline event")
		}
	case <-time.After(time.Second):
		t.Fatal("disconnect event not received")
	}
}
