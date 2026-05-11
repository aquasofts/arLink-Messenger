package auth

import (
	"crypto/ed25519"
	"encoding/base64"
	"encoding/json"
	"testing"
	"time"
)

var b64u = base64.RawURLEncoding

func TestIssueAndVerify_OK(t *testing.T) {
	pub, priv, err := ed25519.GenerateKey(nil)
	if err != nil {
		t.Fatal(err)
	}
	deviceID := DeviceIDFromPubKey(pub)

	c := New(60 * time.Second)
	ch, err := c.Issue(deviceID)
	if err != nil {
		t.Fatal(err)
	}
	nonce, _ := b64u.DecodeString(ch.NonceB64)

	payload := authPayload{
		DeviceID: deviceID, ChallengeID: ch.ChallengeID,
		PubkeyB64: b64u.EncodeToString(pub), Ts: time.Now().UnixMilli(),
	}
	pBytes, _ := json.Marshal(payload)
	sig := ed25519.Sign(priv, append(pBytes, nonce...))
	header := b64u.EncodeToString(pBytes) + "." + b64u.EncodeToString(sig)

	gotID, gotPub, err := c.Verify(header, 5*time.Minute)
	if err != nil {
		t.Fatalf("verify failed: %v", err)
	}
	if gotID != deviceID {
		t.Fatalf("device_id mismatch: %s", gotID)
	}
	if string(gotPub) != string(pub) {
		t.Fatal("pub mismatch")
	}
}

func TestVerify_BadSignature(t *testing.T) {
	pub, _, _ := ed25519.GenerateKey(nil)
	deviceID := DeviceIDFromPubKey(pub)
	c := New(60 * time.Second)
	ch, _ := c.Issue(deviceID)

	payload := authPayload{
		DeviceID: deviceID, ChallengeID: ch.ChallengeID,
		PubkeyB64: b64u.EncodeToString(pub), Ts: time.Now().UnixMilli(),
	}
	pBytes, _ := json.Marshal(payload)
	badSig := make([]byte, ed25519.SignatureSize)
	header := b64u.EncodeToString(pBytes) + "." + b64u.EncodeToString(badSig)
	if _, _, err := c.Verify(header, 5*time.Minute); err == nil {
		t.Fatal("expected signature failure")
	}
}

func TestVerify_DeviceIDMismatch(t *testing.T) {
	pub, priv, _ := ed25519.GenerateKey(nil)
	c := New(60 * time.Second)
	// 颁发给 deviceA，但 payload 用 deviceB（与 pub 不一致）
	deviceA := DeviceIDFromPubKey(pub)
	ch, _ := c.Issue(deviceA)
	nonce, _ := b64u.DecodeString(ch.NonceB64)

	payload := authPayload{
		DeviceID: "fakeXX", ChallengeID: ch.ChallengeID,
		PubkeyB64: b64u.EncodeToString(pub), Ts: time.Now().UnixMilli(),
	}
	pBytes, _ := json.Marshal(payload)
	sig := ed25519.Sign(priv, append(pBytes, nonce...))
	header := b64u.EncodeToString(pBytes) + "." + b64u.EncodeToString(sig)

	if _, _, err := c.Verify(header, 5*time.Minute); err == nil {
		t.Fatal("expected device mismatch")
	}
}

func TestChallenge_OneShot(t *testing.T) {
	pub, priv, _ := ed25519.GenerateKey(nil)
	deviceID := DeviceIDFromPubKey(pub)
	c := New(60 * time.Second)
	ch, _ := c.Issue(deviceID)
	nonce, _ := b64u.DecodeString(ch.NonceB64)

	payload := authPayload{
		DeviceID: deviceID, ChallengeID: ch.ChallengeID,
		PubkeyB64: b64u.EncodeToString(pub), Ts: time.Now().UnixMilli(),
	}
	pBytes, _ := json.Marshal(payload)
	sig := ed25519.Sign(priv, append(pBytes, nonce...))
	header := b64u.EncodeToString(pBytes) + "." + b64u.EncodeToString(sig)

	if _, _, err := c.Verify(header, 5*time.Minute); err != nil {
		t.Fatal(err)
	}
	// 第二次同 challenge 必须失败
	if _, _, err := c.Verify(header, 5*time.Minute); err == nil {
		t.Fatal("expected reuse to fail (one-shot challenge)")
	}
}
