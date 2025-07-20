import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { MeetingNotesService } from './meeting-notes.service';
import { MeetingNotesApi } from './meeting-notes.api';
import { ProjectState } from '../states/project.state';
import { MeetingNote } from '../models/meeting-note.model';

describe('MeetingNotesService', () => {
  let service: MeetingNotesService;
  let mockMeetingNotesApi: jasmine.SpyObj<MeetingNotesApi>;
  let mockProjectState: jasmine.SpyObj<ProjectState>;

  const mockMeetingNotes: MeetingNote[] = [
    { id: 'note1', name: 'Meeting 1', loading: false },
    { id: 'note2', name: '', loading: true },
    { id: 'note3', name: 'Meeting 3', loading: false }
  ];

  beforeEach(() => {
    const meetingNotesApiSpy = jasmine.createSpyObj('MeetingNotesApi', [
      'getMeetingNotesMetadata', 'uploadMeetingNoteFile', 'getMeetingNote'
    ]);
    const projectStateSpy = jasmine.createSpyObj('ProjectState', [
      'setMeetingNotes', 'updateMeetingNote'
    ]);

    TestBed.configureTestingModule({
      providers: [
        MeetingNotesService,
        { provide: MeetingNotesApi, useValue: meetingNotesApiSpy },
        { provide: ProjectState, useValue: projectStateSpy }
      ]
    });

    service = TestBed.inject(MeetingNotesService);
    mockMeetingNotesApi = TestBed.inject(MeetingNotesApi) as jasmine.SpyObj<MeetingNotesApi>;
    mockProjectState = TestBed.inject(ProjectState) as jasmine.SpyObj<ProjectState>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('loadMeetingNotes', () => {
    it('should load meeting notes and update state', () => {
      mockMeetingNotesApi.getMeetingNotesMetadata.and.returnValue(of(mockMeetingNotes));
      spyOn(service as unknown as { pollMeetingNote: (projectId: string, noteId: string) => void }, 'pollMeetingNote');
      
      service.loadMeetingNotes('project-1').subscribe(notes => {
        expect(notes).toEqual(mockMeetingNotes);
      });
      
      expect(mockMeetingNotesApi.getMeetingNotesMetadata).toHaveBeenCalledWith('project-1');
      expect(mockProjectState.setMeetingNotes).toHaveBeenCalledWith('project-1', jasmine.any(Array));
    });

    it('should set default name for notes without names', () => {
      const notesWithoutNames: Partial<MeetingNote>[] = [
        { id: 'note1', name: '', loading: false },
        { id: 'note2', name: undefined, loading: false }
      ];
      mockMeetingNotesApi.getMeetingNotesMetadata.and.returnValue(of(notesWithoutNames as MeetingNote[]));
      
      service.loadMeetingNotes('project-1').subscribe();
      
      expect(mockProjectState.setMeetingNotes).toHaveBeenCalledWith('project-1', 
        jasmine.arrayContaining([
          jasmine.objectContaining({ name: 'Note note1' }),
          jasmine.objectContaining({ name: 'Note note2' })
        ])
      );
    });

    it('should trigger polling for loading notes', fakeAsync(() => {
      spyOn(service as unknown as { pollMeetingNote: (projectId: string, noteId: string) => void }, 'pollMeetingNote');
      mockMeetingNotesApi.getMeetingNotesMetadata.and.returnValue(of(mockMeetingNotes));
      
      service.loadMeetingNotes('project-1').subscribe();
      tick();
      
      expect((service as unknown as { pollMeetingNote: (projectId: string, noteId: string) => void }).pollMeetingNote).toHaveBeenCalledWith('project-1', 'note2');
    }));
  });

  describe('uploadMeetingNoteFile', () => {
    const mockFile = new File(['test'], 'test.wav', { type: 'audio/wav' });
    const uploadedNote: MeetingNote = { id: 'uploaded-note', name: '', loading: true };

    it('should upload file and update state', () => {
      mockMeetingNotesApi.uploadMeetingNoteFile.and.returnValue(of(uploadedNote));
      spyOn(service as unknown as { pollMeetingNote: (projectId: string, noteId: string) => void }, 'pollMeetingNote');
      spyOn(console, 'log');
      
      service.uploadMeetingNoteFile('project-1', 2, mockFile).subscribe(note => {
        expect(note).toEqual(uploadedNote);
      });
      
      expect(mockMeetingNotesApi.uploadMeetingNoteFile).toHaveBeenCalledWith('project-1', 2, mockFile);
      expect(console.log).toHaveBeenCalledWith('Meeting note uploaded:', jasmine.any(Object));
      expect(mockProjectState.updateMeetingNote).toHaveBeenCalledWith('project-1', jasmine.any(Object));
    });

    it('should preserve existing name when uploading', () => {
      const namedUpload: MeetingNote = { id: 'named-upload', name: 'Custom Name', loading: false };
      mockMeetingNotesApi.uploadMeetingNoteFile.and.returnValue(of(namedUpload));
      spyOn(console, 'log');
      
      service.uploadMeetingNoteFile('project-1', 2, mockFile).subscribe();
      
      expect(mockProjectState.updateMeetingNote).toHaveBeenCalledWith('project-1', 
        jasmine.objectContaining({ name: 'Custom Name' })
      );
    });

    it('should trigger polling for uploaded loading note', () => {
      const loadingUpload: MeetingNote = { id: 'loading-upload', name: '', loading: true };
      mockMeetingNotesApi.uploadMeetingNoteFile.and.returnValue(of(loadingUpload));
      spyOn(service as unknown as { pollMeetingNote: (projectId: string, noteId: string) => void }, 'pollMeetingNote');
      spyOn(console, 'log');
      
      service.uploadMeetingNoteFile('project-1', 2, mockFile).subscribe();
      
      expect((service as unknown as { pollMeetingNote: (projectId: string, noteId: string) => void }).pollMeetingNote).toHaveBeenCalledWith('project-1', 'loading-upload');
    });

    it('should not trigger polling for completed uploaded note', () => {
      const completedUpload: MeetingNote = { id: 'completed-upload', name: 'Completed Note', loading: false };
      mockMeetingNotesApi.uploadMeetingNoteFile.and.returnValue(of(completedUpload));
      spyOn(service as unknown as { pollMeetingNote: (projectId: string, noteId: string) => void }, 'pollMeetingNote');
      spyOn(console, 'log');
      
      service.uploadMeetingNoteFile('project-1', 2, mockFile).subscribe();
      
      expect((service as unknown as { pollMeetingNote: (projectId: string, noteId: string) => void }).pollMeetingNote).not.toHaveBeenCalled();
    });
  });

  describe('pollMeetingNote (private method)', () => {
    beforeEach(() => {
      jasmine.clock().install();
    });

    afterEach(() => {
      jasmine.clock().uninstall();
    });

    it('should poll until note is no longer loading', fakeAsync(() => {
      const completedNote: MeetingNote = { id: 'polling-note', name: 'Polling Note', loading: false };
      
      mockMeetingNotesApi.getMeetingNote.and.returnValue(of(completedNote));
      
      (service as unknown as { pollMeetingNote: (projectId: string, noteId: string) => void }).pollMeetingNote('project-1', 'polling-note');
      tick(5000);
      
      expect(mockMeetingNotesApi.getMeetingNote).toHaveBeenCalledWith('project-1', 'polling-note');
      expect(mockProjectState.updateMeetingNote).toHaveBeenCalledWith('project-1', completedNote);
    }));

    it('should stop polling after 10 attempts', fakeAsync(() => {
      spyOn(console, 'warn');
      
      (service as unknown as { pollMeetingNote: (projectId: string, noteId: string, count?: number) => void }).pollMeetingNote('project-1', 'stuck-note', 10);
      tick(5000);
      
      expect(mockMeetingNotesApi.getMeetingNote).not.toHaveBeenCalled();
      expect(console.warn).toHaveBeenCalledWith('Polling stopped for note stuck-note after 10 attempts.');
    }));
  });
});
