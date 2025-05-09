import { TestBed } from '@angular/core/testing';
import { provideHttpClient} from '@angular/common/http';
import { HelloService } from './hello.service';

describe('HelloService', () => {
  let service: HelloService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [HelloService, provideHttpClient()],
    });
    service = TestBed.inject(HelloService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
