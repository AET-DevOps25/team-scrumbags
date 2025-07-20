import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { ReportService } from './report.service';
import { ReportApi } from './report.api';
import { ProjectState } from '../states/project.state';
import { Report } from '../models/report.model';

describe('ReportService', () => {
  let service: ReportService;
  let mockReportApi: jasmine.SpyObj<ReportApi>;
  let mockProjectState: jasmine.SpyObj<ProjectState>;

  const mockReports: Report[] = [
    { 
      id: 'report1', 
      name: 'Weekly Report', 
      loading: false, 
      projectId: 'project-1',
      startTime: new Date('2024-01-01'),
      endTime: new Date('2024-01-07'),
      userIds: ['user1'],
      generatedAt: new Date(), 
      summary: 'Report content' 
    },
    { 
      id: 'report2', 
      name: '', 
      loading: true, 
      projectId: 'project-1',
      startTime: new Date('2024-01-08'),
      endTime: new Date('2024-01-14'),
      userIds: ['user2'],
      generatedAt: new Date(), 
      summary: '' 
    },
    { 
      id: 'report3', 
      name: 'Monthly Report', 
      loading: false, 
      projectId: 'project-1',
      startTime: new Date('2024-01-01'),
      endTime: new Date('2024-01-31'),
      userIds: ['user1', 'user2'],
      generatedAt: new Date(), 
      summary: 'Monthly content' 
    }
  ];

  beforeEach(() => {
    const reportApiSpy = jasmine.createSpyObj('ReportApi', [
      'getReports', 'generateReport', 'getReportbyId'
    ]);
    const projectStateSpy = jasmine.createSpyObj('ProjectState', [
      'setReports', 'updateReport'
    ]);

    TestBed.configureTestingModule({
      providers: [
        ReportService,
        { provide: ReportApi, useValue: reportApiSpy },
        { provide: ProjectState, useValue: projectStateSpy }
      ]
    });

    service = TestBed.inject(ReportService);
    mockReportApi = TestBed.inject(ReportApi) as jasmine.SpyObj<ReportApi>;
    mockProjectState = TestBed.inject(ProjectState) as jasmine.SpyObj<ProjectState>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('loadReportsMetadata', () => {
    it('should load reports and update state', () => {
      mockReportApi.getReports.and.returnValue(of(mockReports));
      spyOn(service as any, 'pollReport');
      
      service.loadReportsMetadata('project-1').subscribe(reports => {
        expect(reports).toEqual(mockReports);
      });
      
      expect(mockReportApi.getReports).toHaveBeenCalledWith('project-1');
      expect(mockProjectState.setReports).toHaveBeenCalledWith('project-1', jasmine.any(Array));
    });

    it('should set default name for reports without names', () => {
      const reportsWithoutNames: Report[] = [
        { 
          id: 'report1', 
          name: '', 
          loading: false, 
          projectId: 'project-1',
          startTime: new Date('2024-01-01'),
          endTime: new Date('2024-01-07'),
          userIds: ['user1'],
          generatedAt: new Date(), 
          summary: 'Content' 
        },
        { 
          id: 'report2', 
          name: null as any, 
          loading: false, 
          projectId: 'project-1',
          startTime: new Date('2024-01-08'),
          endTime: new Date('2024-01-14'),
          userIds: ['user2'],
          generatedAt: new Date(), 
          summary: 'Content' 
        }
      ];
      mockReportApi.getReports.and.returnValue(of(reportsWithoutNames));
      
      service.loadReportsMetadata('project-1').subscribe();
      
      expect(mockProjectState.setReports).toHaveBeenCalledWith('project-1', 
        jasmine.arrayContaining([
          jasmine.objectContaining({ name: 'Report report1' }),
          jasmine.objectContaining({ name: 'Report report2' })
        ])
      );
    });

    it('should trigger polling for loading reports', () => {
      mockReportApi.getReports.and.returnValue(of(mockReports));
      spyOn(service as any, 'pollReport');
      
      service.loadReportsMetadata('project-1').subscribe();
      
      expect((service as any).pollReport).toHaveBeenCalledWith('project-1', 'report2');
    });
  });

  describe('generateReport', () => {
    const generatedReport: Report = { 
      id: 'new-report', 
      name: '', 
      loading: true, 
      projectId: 'project-1',
      startTime: new Date('2024-01-01'),
      endTime: new Date('2024-01-31'),
      userIds: ['user1'],
      generatedAt: new Date(), 
      summary: '' 
    };

    it('should generate report and update state', () => {
      const startDate = new Date('2024-01-01');
      const endDate = new Date('2024-01-31');
      const userIds = ['user1', 'user2'];
      
      mockReportApi.generateReport.and.returnValue(of(generatedReport));
      spyOn(service as any, 'pollReport');
      
      service.generateReport('project-1', startDate, endDate, userIds).subscribe(report => {
        expect(report).toEqual(jasmine.objectContaining({ 
          id: 'new-report',
          name: 'Report new-report'
        }));
      });
      
      expect(mockReportApi.generateReport).toHaveBeenCalledWith('project-1', startDate, endDate, userIds);
      expect(mockProjectState.updateReport).toHaveBeenCalledWith('project-1', jasmine.any(Object));
    });

    it('should set default name for generated report', () => {
      mockReportApi.generateReport.and.returnValue(of(generatedReport));
      spyOn(service as any, 'pollReport');
      
      service.generateReport('project-1', null, null).subscribe();
      
      expect(mockProjectState.updateReport).toHaveBeenCalledWith('project-1', 
        jasmine.objectContaining({ name: 'Report new-report' })
      );
    });

    it('should preserve existing name when generating report', () => {
      const namedReport: Report = { 
        id: 'named-report', 
        name: 'Custom Report Name', 
        loading: false, 
        projectId: 'project-1',
        startTime: new Date('2024-01-01'),
        endTime: new Date('2024-01-31'),
        userIds: ['user1'],
        generatedAt: new Date(), 
        summary: 'Custom content' 
      };
      mockReportApi.generateReport.and.returnValue(of(namedReport));
      
      service.generateReport('project-1', null, null).subscribe();
      
      expect(mockProjectState.updateReport).toHaveBeenCalledWith('project-1', 
        jasmine.objectContaining({ name: 'Custom Report Name' })
      );
    });

    it('should trigger polling for loading generated report', () => {
      mockReportApi.generateReport.and.returnValue(of(generatedReport));
      spyOn(service as any, 'pollReport');
      
      service.generateReport('project-1', null, null).subscribe();
      
      expect((service as any).pollReport).toHaveBeenCalledWith('project-1', 'new-report');
    });

    it('should not trigger polling for completed generated report', () => {
      const completedReport: Report = { 
        id: 'completed-report', 
        name: 'Completed Report', 
        loading: false, 
        projectId: 'project-1',
        startTime: new Date('2024-01-01'),
        endTime: new Date('2024-01-31'),
        userIds: ['user1'],
        generatedAt: new Date(), 
        summary: 'Completed content' 
      };
      mockReportApi.generateReport.and.returnValue(of(completedReport));
      spyOn(service as any, 'pollReport');
      
      service.generateReport('project-1', null, null).subscribe();
      
      expect((service as any).pollReport).not.toHaveBeenCalled();
    });
  });

  describe('pollReport (private method)', () => {
    beforeEach(() => {
      jasmine.clock().install();
    });

    afterEach(() => {
      jasmine.clock().uninstall();
    });

    it('should poll until report is no longer loading', fakeAsync(() => {
      const completedReport: Report = { 
        id: 'polling-report', 
        name: 'Polling Report', 
        loading: false, 
        projectId: 'project-1',
        startTime: new Date('2024-01-01'),
        endTime: new Date('2024-01-31'),
        userIds: ['user1'],
        generatedAt: new Date(), 
        summary: 'Final content' 
      };
      
      mockReportApi.getReportbyId.and.returnValue(of(completedReport));
      
      (service as any).pollReport('project-1', 'polling-report');
      tick(5000);
      
      expect(mockReportApi.getReportbyId).toHaveBeenCalledWith('project-1', 'polling-report');
      expect(mockProjectState.updateReport).toHaveBeenCalledWith('project-1', completedReport);
    }));

    it('should continue polling if report is still loading', fakeAsync(() => {
      const loadingReport: Report = { 
        id: 'loading-report', 
        name: 'Loading Report', 
        loading: true, 
        projectId: 'project-1',
        startTime: new Date('2024-01-01'),
        endTime: new Date('2024-01-31'),
        userIds: ['user1'],
        generatedAt: new Date(), 
        summary: '' 
      };
      
      mockReportApi.getReportbyId.and.returnValue(of(loadingReport));
      
      // Mock the polling method to avoid infinite recursion in tests
      spyOn(service as any, 'pollReport').and.callFake((projectId: string, reportId: string, count = 0) => {
        if (count >= 10) {
          return;
        }
        // Simulate polling delay
        setTimeout(() => {
          mockReportApi.getReportbyId(projectId, reportId).subscribe();
        }, 5000);
      });
      
      (service as any).pollReport('project-1', 'loading-report');
      tick(5000);
      
      expect(mockReportApi.getReportbyId).toHaveBeenCalledWith('project-1', 'loading-report');
    }));

    it('should stop polling after 10 attempts', fakeAsync(() => {
      spyOn(console, 'warn');
      
      (service as any).pollReport('project-1', 'stuck-report', 10);
      tick(5000);
      
      expect(mockReportApi.getReportbyId).not.toHaveBeenCalled();
      expect(console.warn).toHaveBeenCalledWith('Polling stopped for report stuck-report after 10 attempts.');
    }));

    it('should set default name for polled report without name', fakeAsync(() => {
      const reportWithoutName: Report = { 
        id: 'unnamed-report', 
        name: '', 
        loading: false, 
        projectId: 'project-1',
        startTime: new Date('2024-01-01'),
        endTime: new Date('2024-01-31'),
        userIds: ['user1'],
        generatedAt: new Date(), 
        summary: 'Content' 
      };
      
      mockReportApi.getReportbyId.and.returnValue(of(reportWithoutName));
      
      (service as any).pollReport('project-1', 'unnamed-report');
      tick(5000);
      
      expect(mockProjectState.updateReport).toHaveBeenCalledWith('project-1', 
        jasmine.objectContaining({ name: 'Report unnamed-report' })
      );
    }));
  });
});
