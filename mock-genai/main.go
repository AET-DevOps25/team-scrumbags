package main

import (
	"fmt"
	"net/http"
	"time"

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

	r.GET("projects/:projectId/reports", GetReportsMetadata)
	r.GET("projects/:projectId/reports/:id/content", GetReportContent)
	r.POST("projects/:projectId/reports", GenerateReport)

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

type Report struct {
	ID       uuid.UUID `json:"id"`
	Name string    `json:"name"`
	PeriodStart time.Time `json:"periodStart"`
	PeriodEnd   time.Time `json:"periodEnd"`
	UserIds     []string `json:"userIds"`
	Content     string   `json:"content,omitempty"`
}

type ReportParams struct {
	PeriodStart *time.Time `json:"periodStart"`
	PeriodEnd   *time.Time `json:"periodEnd"`
	UserIds     []string  `json:"userIds"`
}

var storage = make(map[uuid.UUID]Report)

func GetReportsMetadata(c *gin.Context) {
	reports := make([]Report, 0) 
	for _, report := range storage {
		report.Content = "" // Exclude content for metadata endpoint
		reports = append(reports, report)
	}

	c.JSON(200, reports)
}

func GetReportContent(c *gin.Context) {
	idStr := c.Param("id")
	id, err := uuid.Parse(idStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid UUID"})
		return
	}
	report, ok := storage[id]
	if !ok {
		c.JSON(http.StatusNotFound, gin.H{"error": "Report not found"})
		return
	}

	c.JSON(200, report)
}

func GenerateReport(c *gin.Context) {
	var body ReportParams
	err := c.BindJSON(&body)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid parameters"})
		return
	}
	log.WithFields(log.Fields{
		"PeriodStart": body.PeriodStart,
		"PeriodEnd":   body.PeriodEnd,
		"UserIds":     body.UserIds,
	}).Debug("Generating report with parameters")

	if body.PeriodStart == nil {
		body.PeriodStart = &time.Time{}
	}
	if body.PeriodEnd == nil {
		now := time.Now()
		body.PeriodEnd = &now
	}

	log.WithFields(log.Fields{
		"PeriodStart": body.PeriodStart,
		"PeriodEnd":   body.PeriodEnd,
		"UserIds":     body.UserIds,
	}).Debug("Params after defaults applied")

	id, err := uuid.NewRandom()
	if err != nil {
		log.WithError(err).Error("Failed to generate UUID")
		c.JSON(500, gin.H{"error": "Internal server error"})
		return
	}
	content := GenerateRandomText()

	report := Report{
		ID:          id,
		Name:        fmt.Sprintf("Report %s", id),
		PeriodStart: *body.PeriodStart,
		PeriodEnd:   *body.PeriodEnd,
		UserIds:     body.UserIds,
		Content:     content,
	}
	storage[id] = report

	c.JSON(201, report)
}
