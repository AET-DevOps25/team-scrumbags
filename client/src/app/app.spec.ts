import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { App } from './app';

// Mock component for RouterOutlet
@Component({
  selector: 'app-router-outlet',
  template: '<div>Router Outlet Mock</div>'
})
class MockRouterOutletComponent {}

describe('App', () => {
  let component: App;
  let fixture: ComponentFixture<App>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App]
    })
    .overrideComponent(App, {
      remove: { imports: [] },
      add: { imports: [MockRouterOutletComponent] }
    })
    .compileComponents();

    fixture = TestBed.createComponent(App);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have title "trace-client"', () => {
    expect((component as { title: string }).title).toEqual('trace-client');
  });

  it('should render router outlet', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-router-outlet')).toBeTruthy();
  });

  it('should have correct selector', () => {
    const appElement = fixture.nativeElement as HTMLElement;
    expect(appElement.tagName.toLowerCase()).toBe('app-root');
  });
});