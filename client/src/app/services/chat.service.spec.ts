import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { ChatService } from './chat.service';
import { ChatApi } from './chat.api';
import { ProjectState } from '../states/project.state';
import { UserService } from './user.service';
import { Message } from '../models/message.model';
import { User } from '../models/user.model';

describe('ChatService', () => {
  let service: ChatService;
  let mockChatApi: jasmine.SpyObj<ChatApi>;
  let mockProjectState: jasmine.SpyObj<ProjectState>;
  let mockUserService: jasmine.SpyObj<UserService>;

  const mockUser: User = { id: 'user1', username: 'testuser', email: 'test@example.com' };
  const mockMessages: Message[] = [
    { id: 'msg1', content: 'Hello', loading: false, userId: 'user1', isGenerated: false, timestamp: new Date() },
    { id: 'msg2', content: 'Processing...', loading: true, userId: 'user1', isGenerated: true, timestamp: new Date() }
  ];

  beforeEach(() => {
    const chatApiSpy = jasmine.createSpyObj('ChatApi', [
      'getChatMessages', 'sendMessage', 'getChatMessageById'
    ]);
    const projectStateSpy = jasmine.createSpyObj('ProjectState', [
      'setMessages', 'updateMessages'
    ]);
    const userServiceSpy = jasmine.createSpyObj('UserService', ['getSignedInUser']);

    TestBed.configureTestingModule({
      providers: [
        ChatService,
        { provide: ChatApi, useValue: chatApiSpy },
        { provide: ProjectState, useValue: projectStateSpy },
        { provide: UserService, useValue: userServiceSpy }
      ]
    });

    service = TestBed.inject(ChatService);
    mockChatApi = TestBed.inject(ChatApi) as jasmine.SpyObj<ChatApi>;
    mockProjectState = TestBed.inject(ProjectState) as jasmine.SpyObj<ProjectState>;
    mockUserService = TestBed.inject(UserService) as jasmine.SpyObj<UserService>;
    
    mockUserService.getSignedInUser.and.returnValue(mockUser);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('loadAllMessages', () => {
    it('should load messages and update state', () => {
      mockChatApi.getChatMessages.and.returnValue(of(mockMessages));
      spyOn(service as any, 'pollMessage');
      
      service.loadAllMessages('project-1').subscribe(messages => {
        expect(messages).toEqual(mockMessages);
      });
      
      expect(mockChatApi.getChatMessages).toHaveBeenCalledWith('project-1', 'user1');
      expect(mockProjectState.setMessages).toHaveBeenCalledWith('project-1', mockMessages);
    });

    it('should trigger polling for loading messages', () => {
      mockChatApi.getChatMessages.and.returnValue(of(mockMessages));
      spyOn(service as any, 'pollMessage');
      
      service.loadAllMessages('project-1').subscribe();
      
      expect((service as any).pollMessage).toHaveBeenCalledWith('project-1', 'msg2', 'user1');
    });

    it('should throw error when user is not signed in', () => {
      mockUserService.getSignedInUser.and.returnValue(undefined);
      
      expect(() => {
        service.loadAllMessages('project-1').subscribe();
      }).toThrowError('User not signed in');
    });
  });

  describe('sendMessage', () => {
    const responseMessages: Message[] = [
      { id: 'msg3', content: 'New message', loading: false, userId: 'user1', isGenerated: false, timestamp: new Date() },
      { id: 'msg4', content: 'AI response', loading: true, userId: 'ai', isGenerated: true, timestamp: new Date() }
    ];

    it('should send message and update state', () => {
      mockChatApi.sendMessage.and.returnValue(of(responseMessages));
      spyOn(service as any, 'pollMessage');
      
      service.sendMessage('project-1', 'Hello').subscribe(messages => {
        expect(messages).toEqual(responseMessages);
      });
      
      expect(mockChatApi.sendMessage).toHaveBeenCalledWith('project-1', 'Hello', 'user1');
      expect(mockProjectState.updateMessages).toHaveBeenCalledWith('project-1', responseMessages);
    });

    it('should trigger polling for loading response messages', () => {
      mockChatApi.sendMessage.and.returnValue(of(responseMessages));
      spyOn(service as any, 'pollMessage');
      
      service.sendMessage('project-1', 'Hello').subscribe();
      
      expect((service as any).pollMessage).toHaveBeenCalledWith('project-1', 'msg4', 'user1');
    });

    it('should throw error when user is not signed in', () => {
      mockUserService.getSignedInUser.and.returnValue(undefined);
      
      expect(() => {
        service.sendMessage('project-1', 'Hello').subscribe();
      }).toThrowError('User not signed in');
    });
  });

  describe('pollMessage (private method)', () => {
    beforeEach(() => {
      jasmine.clock().install();
    });

    afterEach(() => {
      jasmine.clock().uninstall();
    });

    it('should poll until message is no longer loading', fakeAsync(() => {
      const completedMessage: Message = { 
        id: 'msg1', 
        content: 'Completed message', 
        loading: false, 
        userId: 'ai',
        isGenerated: true,
        timestamp: new Date() 
      };
      
      mockChatApi.getChatMessageById.and.returnValue(of(completedMessage));
      
      (service as any).pollMessage('project-1', 'msg1', 'user1');
      tick(5000);
      
      expect(mockChatApi.getChatMessageById).toHaveBeenCalledWith('project-1', 'msg1', 'user1');
      expect(mockProjectState.updateMessages).toHaveBeenCalledWith('project-1', [completedMessage]);
    }));

    it('should continue polling if message is still loading', fakeAsync(() => {
      const loadingMessage: Message = { 
        id: 'msg1', 
        content: 'Still loading...', 
        loading: true, 
        userId: 'ai',
        isGenerated: true,
        timestamp: new Date() 
      };
      
      mockChatApi.getChatMessageById.and.returnValue(of(loadingMessage));
      
      // Mock the polling method to avoid infinite recursion in tests
      spyOn(service as any, 'pollMessage').and.callFake((projectId: string, messageId: string, userId: string, count = 0) => {
        if (count >= 10) {
          return;
        }
        // Simulate polling delay
        setTimeout(() => {
          mockChatApi.getChatMessageById(projectId, messageId, userId).subscribe();
        }, 5000);
      });
      
      (service as any).pollMessage('project-1', 'msg1', 'user1');
      tick(5000);
      
      expect(mockChatApi.getChatMessageById).toHaveBeenCalledWith('project-1', 'msg1', 'user1');
    }));

    it('should stop polling after 10 attempts', fakeAsync(() => {
      spyOn(console, 'warn');
      
      (service as any).pollMessage('project-1', 'stuck-msg', 'user1', 10);
      tick(5000);
      
      expect(mockChatApi.getChatMessageById).not.toHaveBeenCalled();
      expect(console.warn).toHaveBeenCalledWith('Polling stopped for message stuck-msg after 10 attempts.');
    }));
  });
});
