import { FormBuilder } from '@angular/forms';
import { of } from 'rxjs';
import { AdminUsersComponent } from './admin-users.component';

describe('AdminUsersComponent', () => {
  it('separates configured teams from teams without an account', () => {
    const component = new AdminUsersComponent(
      new FormBuilder(),
      { getUsers: () => of([]) } as any,
      { getAllParticipants: () => of([]) } as any,
      { open: () => undefined } as any
    );
    component.participants = [{ id: 1, name: 'Prima' }, { id: 2, name: 'Seconda' }];
    component.users = [
      { username: 'utente1', participantId: 1, participantName: 'Prima' },
      { username: 'admin', participantId: null, role: 'admin' }
    ];

    expect(component.configuredParticipants).toBe(1);
    expect(component.unconfiguredParticipants.map(p => p.name)).toEqual(['Seconda']);
  });

});
