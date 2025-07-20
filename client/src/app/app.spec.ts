
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  let component: App;
  let fixture: ComponentFixture<App>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([])
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(App);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have title "trace-client"', () => {
    // Access protected property via bracket notation for test
    expect((component as unknown as { title: string }).title).toEqual('trace-client');
  });

  it('should render router outlet', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    // Angular renders <router-outlet> as a comment node, so check for its presence
    const routerOutlet = compiled.querySelector('router-outlet');
    expect(routerOutlet).toBeTruthy();
  });

  it('should have correct selector', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    // Check that the component was created with the correct selector
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });
});