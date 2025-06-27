package main

import (
	"github.com/caarlos0/env/v11"
	"github.com/joho/godotenv"
	log "github.com/sirupsen/logrus"
)

type Configuration struct {
	Server struct {
		Port uint `env:"SERVER_PORT" envDefault:"8080"`
	}

	Debug bool `env:"DEBUG" envDefault:"false"`
}

var (
	Cfg Configuration
)

func LoadConfig() Configuration {
	err := godotenv.Load("../.env")
	if err != nil {
		log.WithError(err).Warn("Error loading .env file")
	}

	err = env.Parse(&Cfg)
	if err != nil {
		log.WithError(err).Fatal("Error parsing environment variables")
	}

	if Cfg.Debug {
		log.SetLevel(log.DebugLevel)
		log.Warn("DEBUG MODE ENABLED")
	}

	return Cfg
}
