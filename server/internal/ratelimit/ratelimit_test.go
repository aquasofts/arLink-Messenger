package ratelimit

import "testing"

func TestAllowsBurstThenLimits(t *testing.T) {
	l := New(1, 3) // 1 token/s, burst 3
	for i := 0; i < 3; i++ {
		if !l.Allow("d1") {
			t.Fatalf("burst %d should be allowed", i)
		}
	}
	if l.Allow("d1") {
		t.Fatal("expected to be limited after burst")
	}
}

func TestPerDeviceIndependent(t *testing.T) {
	l := New(1, 1)
	if !l.Allow("a") {
		t.Fatal("a first allow")
	}
	if l.Allow("a") {
		t.Fatal("a second should be limited")
	}
	if !l.Allow("b") {
		t.Fatal("b first allow (different bucket)")
	}
}
