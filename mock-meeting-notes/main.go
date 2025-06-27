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

	r.GET("/meetings", GetMeetingMetadata)
	r.GET("/meetings/:id/file", GetMeetingFile)
	r.POST("/meetings", UploadMeetingFile)

	return r
}

type MeetingMetadata struct {
	ID       uuid.UUID `json:"id"`
	FileName string    `json:"name"`
}

var storage = make(map[uuid.UUID]MeetingMetadata)

func GetMeetingMetadata(c *gin.Context) {
	var metadata []MeetingMetadata
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
	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=\"%s\"", meta.FileName))
	c.Header("Content-Type", "application/octet-stream")
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
	storage[id] = MeetingMetadata{ID: id, FileName: header.Filename}
	c.JSON(201, storage[id])
}
