package main

import (
	"fmt"
	"maps"
	"net/http"
	"slices"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt"
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

	r.GET("projects/:projectId/chat", GetAllMessages)
	r.POST("projects/:projectId/chat", SendMessage)

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

func getClaims(c *gin.Context) (jwt.MapClaims, error) {
	authHeader := c.GetHeader("Authorization")
	if !strings.HasPrefix(authHeader, "Bearer ") {
		return nil, fmt.Errorf("no bearer token")
	}
	tokenString := strings.TrimPrefix(authHeader, "Bearer ")

	token, _, err := new(jwt.Parser).ParseUnverified(tokenString, jwt.MapClaims{})
	if err != nil {
		return nil, err
	}

	return token.Claims.(jwt.MapClaims), nil
}

type Report struct {
	ID          uuid.UUID `json:"id"`
	Name        string    `json:"name"`
	PeriodStart time.Time `json:"periodStart"`
	PeriodEnd   time.Time `json:"periodEnd"`
	UserIds     []string  `json:"userIds"`
	Content     string    `json:"content,omitempty"`
}

type ReportParams struct {
	PeriodStart *time.Time `json:"periodStart"`
	PeriodEnd   *time.Time `json:"periodEnd"`
	UserIds     []string   `json:"userIds"`
}

var reportStorage = make(map[uuid.UUID]Report)

func GetReportsMetadata(c *gin.Context) {
	reports := make([]Report, 0)
	for _, report := range reportStorage {
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
	report, ok := reportStorage[id]
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
	content := GenerateRandomTextByLength(5000)

	report := Report{
		ID:          id,
		Name:        fmt.Sprintf("Report %s", id),
		PeriodStart: *body.PeriodStart,
		PeriodEnd:   *body.PeriodEnd,
		UserIds:     body.UserIds,
		Content:     content,
	}
	reportStorage[id] = report

	c.JSON(201, report)
}

type Message struct {
	ID        uuid.UUID `json:"id"`
	Timestamp time.Time `json:"timestamp"`
	UserId    *string   `json:"userId"`
	Content   string    `json:"content"`
	Loading   bool      `json:"loading"`
}

var messageStorage = make(map[string]map[uuid.UUID]Message)

func messageKey(projectId string, userId string) string {
	return fmt.Sprintf("%s:%s", projectId, userId)
}

func GetAllMessages(c *gin.Context) {
	projectIdString := c.Param("projectId")
	claims, err := getClaims(c)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Unauthorized"})
		return
	}
	userId, ok := claims["sub"].(string)
	if !ok || userId == "" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Invalid user ID"})
		return
	}

	messages, ok := messageStorage[messageKey(projectIdString, userId)]
	if !ok {
		c.JSON(http.StatusOK, []Message{})
		return
	}

	c.JSON(200, slices.Collect(maps.Values(messages)))
}

type IncomingMessageDTO struct {
	Message string `json:"message"`
}

func SendMessage(c *gin.Context) {
	projectIdString := c.Param("projectId")
	claims, err := getClaims(c)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Unauthorized"})
		return
	}
	userId, ok := claims["sub"].(string)
	if !ok || userId == "" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Invalid user ID"})
		return
	}

	var incomingMessageDTO IncomingMessageDTO
	if err := c.BindJSON(&incomingMessageDTO); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid message format"})
		return
	}
	incomingMessage := Message{
		ID:        uuid.New(),
		Timestamp: time.Now(),
		UserId:    &userId,
		Content:   incomingMessageDTO.Message,
	}

	key := messageKey(projectIdString, userId)
	if _, ok := messageStorage[key]; !ok {
		messageStorage[key] = make(map[uuid.UUID]Message)
	}

	messageStorage[key][incomingMessage.ID] = incomingMessage

	outgoingMessage := Message{
		ID:        uuid.New(),
		Timestamp: time.Now(),
		UserId:    nil, // No user ID for ai messages
		Content:   "",
		Loading:   true,
	}
	messageStorage[key][outgoingMessage.ID] = outgoingMessage

	// Simulate AI response generation
	time.Sleep(2 * time.Second) // Simulate processing delay
	outgoingMessage.Content = GenerateRandomTextByLength(100)
	outgoingMessage.Loading = false
	messageStorage[key][outgoingMessage.ID] = outgoingMessage

	c.JSON(200, []Message{incomingMessage, outgoingMessage})
}
