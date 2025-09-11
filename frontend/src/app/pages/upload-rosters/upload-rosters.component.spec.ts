import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UploadRostersComponent } from './upload-rosters.component';

describe('UploadRostersComponent', () => {
  let component: UploadRostersComponent;
  let fixture: ComponentFixture<UploadRostersComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ UploadRostersComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(UploadRostersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
