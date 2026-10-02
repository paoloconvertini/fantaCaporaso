import { of, Subject, throwError } from 'rxjs';
import { SearchCallDialogComponent } from './search-call-dialog.component';

describe('SearchCallDialogComponent', () => {
  it('ignores stale searches and clears results for short queries', () => {
    const old = new Subject<any[]>();
    const current = new Subject<any[]>();
    const api = { searchCallPlayers: jasmine.createSpy().and.returnValues(old, current) };
    const component = new SearchCallDialogComponent(api as any, {} as any);
    component.query = 'ab'; component.searchPlayers();
    component.query = 'abc'; component.searchPlayers();
    old.next([{ id: 1 }]);
    expect(component.players).toEqual([]);
    current.next([{ id: 2 }]);
    expect(component.players).toEqual([{ id: 2 }]);
    component.query = 'a'; component.searchPlayers();
    expect(component.players).toEqual([]);
    component.ngOnDestroy();
  });

  it('closes only after successful selection', () => {
    const ref = jasmine.createSpyObj('DialogRef', ['close']);
    const selected = { id: 2, name: 'Test' };
    const component = new SearchCallDialogComponent({ selectCallPlayer: () => of(selected) } as any, ref);
    component.select(selected);
    expect(ref.close).toHaveBeenCalledWith(selected);
  });

  it('keeps the dialog open when the round becomes active', () => {
    const ref = jasmine.createSpyObj('DialogRef', ['close']);
    const component = new SearchCallDialogComponent({ selectCallPlayer: () => throwError(() => ({ error: { error: 'Round attivo' } })) } as any, ref);
    component.select({ id: 2 });
    expect(ref.close).not.toHaveBeenCalled();
    expect(component.error).toBe('Round attivo');
    expect(component.selecting).toBeFalse();
  });
});
