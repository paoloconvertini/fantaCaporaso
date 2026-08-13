import { HttpRequest } from '@angular/common/http';
import { of } from 'rxjs';
import { AuthInterceptor } from './auth.interceptor';

describe('AuthInterceptor', () => {
  it('always sends browser credentials', () => {
    const interceptor = new AuthInterceptor({} as any);
    const next = jasmine.createSpyObj('HttpHandler', ['handle']);
    next.handle.and.returnValue(of({}));

    interceptor.intercept(new HttpRequest('GET', '/api/auth/me'), next).subscribe();

    expect(next.handle).toHaveBeenCalledWith(jasmine.objectContaining({ withCredentials: true }));
  });
});
