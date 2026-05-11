package storage

import (
	"context"
	"errors"

	"github.com/nearlink/nearlink-server/internal/config"
)

// Open 按配置打开 Store。"postgres" 与 "sqlite" 二选一。
func Open(ctx context.Context, c config.Config) (Store, error) {
	switch c.Storage.Driver {
	case "postgres":
		return OpenPostgres(c.Storage.DSN)
	case "sqlite", "":
		return OpenSQLite(c.Storage.DSN)
	default:
		return nil, errors.New("unknown storage driver: " + c.Storage.Driver)
	}
}
