import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';

import { TableColumn, TableComponent } from './table.component';

interface Row {
  name: string;
  amount: string;
}

@Component({
  selector: 'app-table-host',
  standalone: true,
  imports: [TableComponent],
  template: `<app-table [columns]="columns" [rows]="rows" (rowClick)="onRowClick($event)" />`,
})
class HostComponent {
  columns: TableColumn[] = [
    { key: 'name', label: 'Name' },
    { key: 'amount', label: 'Amount' },
  ];
  rows: Row[] = [
    { name: 'Coffee Shop', amount: '-$4.50' },
    { name: 'Payroll', amount: '+$2,000.00' },
  ];
  clicked: Row | null = null;
  onRowClick(row: Row): void {
    this.clicked = row;
  }
}

describe('TableComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HostComponent] });
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders a header cell per column', () => {
    const headers = fixture.nativeElement.querySelectorAll('th');
    expect(headers.length).toBe(2);
    expect(headers[0].textContent).toContain('Name');
    expect(headers[1].textContent).toContain('Amount');
  });

  it('renders a row per data item with cell values from the column keys', () => {
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Coffee Shop');
    expect(rows[0].textContent).toContain('-$4.50');
  });

  it('emits rowClick with the clicked row data', () => {
    const firstRow = fixture.nativeElement.querySelector('tbody tr');
    firstRow.click();
    expect(host.clicked).toEqual(host.rows[0]);
  });
});
