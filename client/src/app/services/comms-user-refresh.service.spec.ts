import { TestBed } from '@angular/core/testing';
import { CommsUserRefreshService } from './comms-user-refresh.service';

describe('CommsUserRefreshService', () => {
  let service: CommsUserRefreshService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CommsUserRefreshService]
    });
    service = TestBed.inject(CommsUserRefreshService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should have onRefreshUsers subject', () => {
    expect(service.onRefreshUsers).toBeDefined();
    expect(typeof service.onRefreshUsers.next).toBe('function');
    expect(typeof service.onRefreshUsers.subscribe).toBe('function');
  });

  it('should allow subscribing to refresh events', () => {
    let eventReceived = false;
    
    service.onRefreshUsers.subscribe(() => {
      eventReceived = true;
    });
    
    service.onRefreshUsers.next(undefined);
    
    expect(eventReceived).toBe(true);
  });

  it('should emit events when next is called', () => {
    const spy = jasmine.createSpy('onRefresh');
    
    service.onRefreshUsers.subscribe(spy);
    service.onRefreshUsers.next(undefined);
    
    expect(spy).toHaveBeenCalled();
  });

  it('should handle multiple subscribers', () => {
    const spy1 = jasmine.createSpy('subscriber1');
    const spy2 = jasmine.createSpy('subscriber2');
    
    service.onRefreshUsers.subscribe(spy1);
    service.onRefreshUsers.subscribe(spy2);
    
    service.onRefreshUsers.next(undefined);
    
    expect(spy1).toHaveBeenCalled();
    expect(spy2).toHaveBeenCalled();
  });
});
