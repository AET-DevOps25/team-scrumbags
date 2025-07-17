import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Component } from '@angular/core';
import { App } from './app';

// Mock component for RouterOutlet
@Component({
  selector: 'router-outlet',
  template: '<div>Router Outlet Mock</div>'
})
class MockRouterOutletComponent {}

describe('App', () => {
  let component: App;
  let fixture: ComponentFixture<App>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    const routerSpyObj = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        { provide: Router, useValue: routerSpyObj }
      ]
    })
    .overrideComponent(App, {
      remove: { imports: [] },
      add: { imports: [MockRouterOutletComponent] }
    })
    .compileComponents();

    fixture = TestBed.createComponent(App);
    component = fixture.componentInstance;
    routerSpy = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have title "trace-client"', () => {
    expect((component as any).title).toEqual('trace-client');
  });

  it('should render router outlet', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });

  it('should have correct selector', () => {
    const appElement = fixture.nativeElement as HTMLElement;
    expect(appElement.tagName.toLowerCase()).toBe('app-root');
  });
});