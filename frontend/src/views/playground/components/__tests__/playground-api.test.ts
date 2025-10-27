import { describe, it, expect, vi, beforeEach } from 'vitest'
import { 
  sendPlaygroundRequest, 
  sendServiceRequest, 
  getRequestSize, 
  getResponseSize,
  formatFileSize,
  formatDuration 
} from '@/api/playground'
import type { PlaygroundRequest, PlaygroundResponse } from '../types/playground'

// Mock axios
vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() }
      }
    }))
  }
}))

describe('Playground API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getRequestSize', () => {
    it('should calculate request size correctly', () => {
      const request: PlaygroundRequest = {
        endpoint: '/api/test',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer token'
        },
        body: { message: 'Hello World' }
      }

      const size = getRequestSize(request)
      expect(size).toBeGreaterThan(0)
    })

    it('should handle requests with files', () => {
      const mockFile = new File(['test content'], 'test.txt', { type: 'text/plain' })
      const request: PlaygroundRequest = {
        endpoint: '/api/upload',
        method: 'POST',
        headers: {},
        files: [mockFile]
      }

      const size = getRequestSize(request)
      expect(size).toBeGreaterThan(mockFile.size)
    })
  })

  describe('getResponseSize', () => {
    it('should calculate response size correctly', () => {
      const response: PlaygroundResponse = {
        status: 200,
        statusText: 'OK',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': '100'
        },
        data: { result: 'success', message: 'Operation completed' },
        duration: 1500,
        timestamp: '2023-01-01T00:00:00.000Z'
      }

      const size = getResponseSize(response)
      expect(size).toBeGreaterThan(0)
    })

    it('should handle blob responses', () => {
      const blob = new Blob(['test content'], { type: 'text/plain' })
      const response: PlaygroundResponse = {
        status: 200,
        statusText: 'OK',
        headers: {},
        data: blob,
        duration: 1000,
        timestamp: '2023-01-01T00:00:00.000Z'
      }

      const size = getResponseSize(response)
      expect(size).toBeGreaterThan(0)
    })
  })

  describe('formatFileSize', () => {
    it('should format bytes correctly', () => {
      expect(formatFileSize(0)).toBe('0 B')
      expect(formatFileSize(500)).toBe('500 B')
      expect(formatFileSize(1024)).toBe('1 KB')
      expect(formatFileSize(1536)).toBe('1.5 KB')
      expect(formatFileSize(1048576)).toBe('1 MB')
      expect(formatFileSize(1073741824)).toBe('1 GB')
    })
  })

  describe('formatDuration', () => {
    it('should format duration correctly', () => {
      expect(formatDuration(500)).toBe('500ms')
      expect(formatDuration(1000)).toBe('1.00s')
      expect(formatDuration(1500)).toBe('1.50s')
      expect(formatDuration(60000)).toBe('1m 0.00s')
      expect(formatDuration(90000)).toBe('1m 30.00s')
    })
  })

  describe('sendServiceRequest', () => {
    it('should build correct request for chat service', async () => {
      const config = {
        model: 'gpt-3.5-turbo',
        messages: [{ role: 'user' as const, content: 'Hello' }],
        temperature: 0.7,
        authorization: 'Bearer token'
      }

      // Mock the actual request to avoid network calls
      const mockSendPlaygroundRequest = vi.fn().mockResolvedValue({
        status: 200,
        statusText: 'OK',
        headers: {},
        data: { choices: [{ message: { content: 'Hi there!' } }] },
        duration: 1000,
        timestamp: '2023-01-01T00:00:00.000Z'
      })

      // Replace the actual function temporarily
      const originalSend = sendPlaygroundRequest
      ;(global as any).sendPlaygroundRequest = mockSendPlaygroundRequest

      try {
        await sendServiceRequest('chat', config)
        
        expect(mockSendPlaygroundRequest).toHaveBeenCalledWith({
          endpoint: '/api/universal/chat/completions',
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer token'
          },
          body: {
            model: 'gpt-3.5-turbo',
            messages: [{ role: 'user', content: 'Hello' }],
            temperature: 0.7
          }
        })
      } finally {
        // Restore original function
        ;(global as any).sendPlaygroundRequest = originalSend
      }
    })

    it('should handle file upload for STT service', async () => {
      const mockFile = new File(['audio content'], 'audio.mp3', { type: 'audio/mpeg' })
      const config = {
        model: 'whisper-1',
        file: mockFile,
        language: 'zh',
        authorization: 'Bearer token'
      }

      const mockSendPlaygroundRequest = vi.fn().mockResolvedValue({
        status: 200,
        statusText: 'OK',
        headers: {},
        data: { text: 'Transcribed text' },
        duration: 2000,
        timestamp: '2023-01-01T00:00:00.000Z'
      })

      const originalSend = sendPlaygroundRequest
      ;(global as any).sendPlaygroundRequest = mockSendPlaygroundRequest

      try {
        await sendServiceRequest('stt', config)
        
        expect(mockSendPlaygroundRequest).toHaveBeenCalledWith({
          endpoint: '/api/universal/audio/transcriptions',
          method: 'POST',
          headers: {
            'Authorization': 'Bearer token'
          },
          body: {
            model: 'whisper-1',
            language: 'zh'
          },
          files: [mockFile]
        })
      } finally {
        ;(global as any).sendPlaygroundRequest = originalSend
      }
    })

    it('should throw error for unsupported service type', async () => {
      await expect(
        sendServiceRequest('unsupported' as any, {})
      ).rejects.toThrow('不支持的服务类型: unsupported')
    })
  })
})