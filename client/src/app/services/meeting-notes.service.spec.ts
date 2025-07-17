import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { MeetingNotesService } from './meeting-notes.service';
import { MeetingNotesApi } from './meeting-notes.api';
import { ProjectState } from '../states/project.state';
import { MeetingNote } from '../models/meeting-note.model';

describe('MeetingNotesService', () => {
  let service: MeetingNotesService;
  let meetingNotesApiSpy: jasmine.SpyObj<MeetingNotesApi>;
  let projectStateSpy: jasmine.SpyObj<ProjectState>;

  const mockMeetingNotes: MeetingNote[] = [
    {
      id: 'note-1',
      name: 'Meeting Note 1',
      loading: false
    },
    {
      id: 'note-2',
      name: '',
      loading: true
    }
  ];

  const mockFile = new File(['audio content'], 'test-meeting.mp3', { type: 'audio/mpeg' });

  beforeEach(() => {
    const meetingNotesApiSpyObj = jasmine.createSpyObj('MeetingNotesApi', [
      'getMeetingNotesMetadata',
      'uploadMeetingNoteFile',
      'getMeetingNote'
    ]);
    
    const projectStateSpyObj = jasmine.createSpyObj('ProjectState', [
      'setMeetingNotes',
      'updateMeetingNote'
    ]);

    TestBed.configureTestingModule({
      providers: [
        MeetingNotesService,
        { provide: MeetingNotesApi, useValue: meetingNotesApiSpyObj },
        { provide: ProjectState, useValue: projectStateSpyObj }
      ]
    });

    service = TestBed.inject(MeetingNotesService);
    meetingNotesApiSpy = TestBed.inject(MeetingNotesApi) as jasmine.SpyObj<MeetingNotesApi>;
    projectStateSpy = TestBed.inject(ProjectState) as jasmine.SpyObj<ProjectState>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('loadMeetingNotes', () => {
    it('should load meeting notes and update state', () => {
      meetingNotesApiSpy.getMeetingNotesMetadata.and.returnValue(of(mockMeetingNotes));

      const result$ = service.loadMeetingNotes('project-1');

      result$.subscribe(notes => {
        expect(notes).toEqual(mockMeetingNotes);
        expect(projectStateSpy.setMeetingNotes).toHaveBeenCalledWith('project-1', mockMeetingNotes);
      });

      expect(meetingNotesApiSpy.getMeetingNotesMetadata).toHaveBeenCalledWith('project-1');
    });

    it('should set default name for notes without names', () => {
      const notesWithoutNames: MeetingNote[] = [
        { id: 'note-1', name: '', loading: false },
        { id: 'note-2', name: '', loading: false }
      ];

      meetingNotesApiSpy.getMeetingNotesMetadata.and.returnValue(of(notesWithoutNames));

      const result$ = service.loadMeetingNotes('project-1');

      result$.subscribe(notes => {
        expect(notes[0].name).toBe('Note note-1');
        expect(notes[1].name).toBe('Note note-2');
      });
    });

    it('should trigger polling for loading notes', fakeAsync(() => {
      const loadingNote: MeetingNote = {
        id: 'loading-note',
        name: 'Loading Note',
        loading: true
      };

      meetingNotesApiSpy.getMeetingNotesMetadata.and.returnValue(of([loadingNote]));
      meetingNotesApiSpy.getMeetingNote.and.returnValue(of({
        ...loadingNote,
        loading: false
      }));

      spyOn(service as any, 'pollMeetingNote').and.callThrough();

      service.loadMeetingNotes('project-1').subscribe();

      expect((service as any).pollMeetingNote).toHaveBeenCalledWith('project-1', 'loading-note');
    }));

    it('should preserve existing names for notes that have names', () => {
      const notesWithNames: MeetingNote[] = [
        { id: 'note-1', name: 'Existing Name', loading: false }
      ];

      meetingNotesApiSpy.getMeetingNotesMetadata.and.returnValue(of(notesWithNames));

      const result$ = service.loadMeetingNotes('project-1');

      result$.subscribe(notes => {
        expect(notes[0].name).toBe('Existing Name');
      });
    });
  });

  describe('uploadMeetingNoteFile', () => {
    it('should upload file and update state', () => {
      const uploadedNote: MeetingNote = {
        id: 'uploaded-note',
        name: '',
        loading: true
      };

      meetingNotesApiSpy.uploadMeetingNoteFile.and.returnValue(of(uploadedNote));
      spyOn(service as any, 'pollMeetingNote').and.stub();

      const result$ = service.uploadMeetingNoteFile('project-1', 2, mockFile);

      result$.subscribe(note => {
        expect(note.name).toBe('Note uploaded-note'); // Should set default name
        expect(projectStateSpy.updateMeetingNote).toHaveBeenCalledWith('project-1', jasmine.objectContaining({
          id: 'uploaded-note',
          name: 'Note uploaded-note'
        }));
      });

      expect(meetingNotesApiSpy.uploadMeetingNoteFile).toHaveBeenCalledWith('project-1', 2, mockFile);
    });

    it('should trigger polling for uploaded loading note', () => {
      const loadingNote: MeetingNote = {
        id: 'loading-upload',
        name: '',
        loading: true
      };

      meetingNotesApiSpy.uploadMeetingNoteFile.and.returnValue(of(loadingNote));
      spyOn(service as any, 'pollMeetingNote').and.stub();

      service.uploadMeetingNoteFile('project-1', 2, mockFile).subscribe();

      expect((service as any).pollMeetingNote).toHaveBeenCalledWith('project-1', 'loading-upload');
    });

    it('should not trigger polling for completed uploaded note', () => {
      const completedNote: MeetingNote = {
        id: 'completed-upload',
        name: 'Completed Note',
        loading: false
      };

      meetingNotesApiSpy.uploadMeetingNoteFile.and.returnValue(of(completedNote));
      spyOn(service as any, 'pollMeetingNote').and.stub();

      service.uploadMeetingNoteFile('project-1', 2, mockFile).subscribe();

      expect((service as any).pollMeetingNote).not.toHaveBeenCalled();
    });

    it('should preserve existing name when uploading', () => {
      const noteWithName: MeetingNote = {
        id: 'named-upload',
        name: 'Custom Name',
        loading: false
      };

      meetingNotesApiSpy.uploadMeetingNoteFile.and.returnValue(of(noteWithName));

      service.uploadMeetingNoteFile('project-1', 2, mockFile).subscribe(note => {
        expect(note.name).toBe('Custom Name');
      });
    });
  });

  describe('pollMeetingNote (private method)', () => {
    it('should poll until note is no longer loading', fakeAsync(() => {
      const initialNote: MeetingNote = {
        id: 'polling-note',
        name: 'Polling Note',
        loading: true
      };

      const completedNote: MeetingNote = {
        ...initialNote,
        loading: false
      };

      // First call returns loading note, second call returns completed note
      meetingNotesApiSpy.getMeetingNote.and.returnValues(
        of(initialNote),
        of(completedNote)
      );

      // Call the private method via bracket notation
      (service as any).pollMeetingNote('project-1', 'polling-note');

      // Wait for the polling delay
      tick(5000);

      expect(meetingNotesApiSpy.getMeetingNote).toHaveBeenCalledWith('project-1', 'polling-note');
      expect(projectStateSpy.updateMeetingNote).toHaveBeenCalledWith('project-1', completedNote);
    }));

    it('should stop polling after 10 attempts', fakeAsync(() => {
      const loadingNote: MeetingNote = {
        id: 'stuck-note',
        name: 'Stuck Note',
        loading: true
      };

      meetingNotesApiSpy.getMeetingNote.and.returnValue(of(loadingNote));
      spyOn(console, 'warn');

      // Start polling with count = 9 (one attempt before limit)
      (service as any).pollMeetingNote('project-1', 'stuck-note', 9);

      tick(5000);

      expect(console.warn).toHaveBeenCalledWith('Polling stopped for note stuck-note after 10 attempts.');
      expect(meetingNotesApiSpy.getMeetingNote).not.toHaveBeenCalled();
    }));

    it('should continue polling when note is still loading and under limit', fakeAsync(() => {
      const loadingNote: MeetingNote = {
        id: 'still-loading',
        name: 'Still Loading',
        loading: true
      };

      meetingNotesApiSpy.getMeetingNote.and.returnValue(of(loadingNote));
      spyOn(service as any, 'pollMeetingNote').and.callThrough();

      // Start with count = 5
      (service as any).pollMeetingNote('project-1', 'still-loading', 5);

      tick(5000);

      expect(meetingNotesApiSpy.getMeetingNote).toHaveBeenCalledWith('project-1', 'still-loading');
      // Should call itself again with count = 6
      expect((service as any).pollMeetingNote).toHaveBeenCalledWith('project-1', 'still-loading', 6);
    }));
  });
});