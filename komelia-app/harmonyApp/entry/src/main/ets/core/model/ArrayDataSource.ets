export class ArrayDataSource<T> implements IDataSource {
  private values: T[] = [];
  private listeners: DataChangeListener[] = [];

  totalCount(): number {
    return this.values.length;
  }

  getData(index: number): T {
    return this.values[index];
  }

  registerDataChangeListener(listener: DataChangeListener): void {
    if (!this.listeners.includes(listener)) this.listeners.push(listener);
  }

  unregisterDataChangeListener(listener: DataChangeListener): void {
    const index = this.listeners.indexOf(listener);
    if (index >= 0) this.listeners.splice(index, 1);
  }

  replace(values: T[]): void {
    this.values = values.slice();
    this.listeners.forEach((listener: DataChangeListener): void => listener.onDataReloaded());
  }
}
