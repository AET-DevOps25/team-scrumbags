package main

import (
	"fmt"
	"io"
	"net/http"
	"os"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	log "github.com/sirupsen/logrus"
)

func main() {
	// Load environment variables
	cfg := LoadConfig()
	log.Infof("Loaded configuration: %+v", cfg.Server)

	log.Infof("Starting GenDev server on port %d", cfg.Server.Port)
	gin.SetMode(gin.ReleaseMode)

	r := SetupRouter()
	log.Panic(r.Run(fmt.Sprintf(":%d", cfg.Server.Port)))
}

func SetupRouter() *gin.Engine {
	r := gin.New()
	r.Use(gin.ErrorLogger())
	r.Use(gin.Recovery())
	r.Use(corsMiddleware())

	r.GET("projects/:projectId/meeting-notes", GetMeetingMetadata)
	r.GET("projects/:projectId/meeting-notes/:id/file", GetMeetingFile)
	r.POST("projects/:projectId/meeting-notes", UploadMeetingFile)

	return r
}

// corsMiddleware sets up CORS headers for all routes
func corsMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Origin, Content-Type, Content-Length, Accept-Encoding, X-CSRF-Token, Authorization")
		c.Header("Access-Control-Expose-Headers", "Content-Length")
		c.Header("Access-Control-Allow-Credentials", "true")

		// Handle preflight requests
		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}

		c.Next()
	}
}

type MeetingMetadata struct {
	ID       uuid.UUID `json:"id"`
	FileName string    `json:"name"`
	MimeType string    `json:"-"`
}

var storage = make(map[uuid.UUID]MeetingMetadata)

func GetMeetingMetadata(c *gin.Context) {
	metadata := make([]MeetingMetadata, 0) 
	for _, m := range storage {
		metadata = append(metadata, m)
	}
	c.JSON(200, metadata)
}

func GetMeetingFile(c *gin.Context) {
	idStr := c.Param("id")
	id, err := uuid.Parse(idStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid UUID"})
		return
	}
	meta, ok := storage[id]
	if !ok {
		c.JSON(http.StatusNotFound, gin.H{"error": "File not found"})
		return
	}
	filePath := fmt.Sprintf("/tmp/%s", id.String())
	file, err := os.Open(filePath)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "File not found"})
		return
	}
	defer file.Close()
	c.Header("Content-Disposition", fmt.Sprintf("inline; filename=\"%s\"", meta.FileName))
	c.Header("Content-Type", meta.MimeType)
	c.File(filePath)
}

func UploadMeetingFile(c *gin.Context) {
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "No file uploaded"})
		return
	}
	defer file.Close()

	id, err := uuid.NewRandom()
	if err != nil {
		log.WithError(err).Error("Failed to generate UUID")
		c.JSON(500, gin.H{"error": "Internal server error"})
		return
	}
	filePath := fmt.Sprintf("/tmp/%s", id.String())
	out, err := os.Create(filePath)
	if err != nil {
		c.JSON(500, gin.H{"error": "Failed to save file"})
		return
	}
	defer out.Close()
	_, err = io.Copy(out, file)
	if err != nil {
		c.JSON(500, gin.H{"error": "Failed to save file"})
		return
	}

	mimeType := header.Header.Get("Content-Type")
	if mimeType == "" {
		mimeType = "application/octet-stream" // Default MIME type if not provided
	}
	storage[id] = MeetingMetadata{ID: id, FileName: header.Filename, MimeType: mimeType}
	c.JSON(201, storage[id])
}
