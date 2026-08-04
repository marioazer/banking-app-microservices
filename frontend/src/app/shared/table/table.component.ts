import { Component, EventEmitter, Input, Output } from '@angular/core';

export interface TableColumn {
  key: string;
  label: string;
}

@Component({
  selector: 'app-table',
  standalone: true,
  template: `
    <table>
      <thead>
        <tr>
          @for (column of columns; track column.key) {
            <th>{{ column.label }}</th>
          }
        </tr>
      </thead>
      <tbody>
        @for (row of rows; track row) {
          <tr (click)="rowClick.emit(row)">
            @for (column of columns; track column.key) {
              <td>{{ cellValue(row, column.key) }}</td>
            }
          </tr>
        }
      </tbody>
    </table>
  `,
})
export class TableComponent<T extends object = object> {
  @Input() columns: TableColumn[] = [];
  @Input() rows: T[] = [];
  @Output() rowClick = new EventEmitter<T>();

  cellValue(row: T, key: string): unknown {
    return (row as Record<string, unknown>)[key];
  }
}
