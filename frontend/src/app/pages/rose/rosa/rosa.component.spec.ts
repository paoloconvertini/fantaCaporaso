import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RosaComponentComponent } from './rosa.component';

describe('RosaComponentComponent', () => {
  let component: RosaComponentComponent;
  let fixture: ComponentFixture<RosaComponentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ RosaComponentComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(RosaComponentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
