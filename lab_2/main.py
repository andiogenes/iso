from string import ascii_uppercase
from prettytable import PrettyTable
import matplotlib
import matplotlib.pyplot as plt
import yaml

# Загрузка входных данных
with open('input.yaml', 'r') as stream:
    try:
        config = yaml.safe_load(stream)
    except yaml.YAMLError as e:
        print(e)

# Матрица маршрутов
F = config['F']
# Матрица норм времени
T = config['T']
# Правило предпочтения ('FGFO' - п.3, 'max_distance' - п.12)
rule = config['rule'] == 'FGFO'

# Текущая длительность проиводственного цикла
time = 0

# Информация о загрузке станков - i-я деталь обрабатывается до достижения t_k
machines = {k: [] for k in ascii_uppercase[:len(F[0]) if len(F) > 0 else 0]}
# Данные по оси абсцисс и ординат графика Ганта
plots = [[] for _ in range(len(F))]
# Метки занятости деталей
busy = [False for _ in range(len(F))]

# Время простоя каждого станка на текущий момент
machine_idles = {k: ((0, -1), 0) for k in ascii_uppercase[:len(F[0]) if len(F) > 0 else 0]}
# Время пролеживания деталей на текущий момент
detail_idles = [((0, -1), 0) for _ in range(len(F))]

def dequeue(name, pipeline):
    """
    Процедура выбора детали из очереди у станка по заданному правилу предпочтения.
    
    name - идентификатор станка;
    pipeline - текущая очередь деталей у станка
    """
    # Текущий номер детали
    id = -1
    # Текущий параметр правила предпочтения
    value = None
    # Текущее время обработки детали
    duration = None
    
    for i, v in enumerate(F):
        # Занятые детали, и детали, ожидающие обработки на другом станке, пропускаются
        if busy[i] or len(v) == 0 or v[0] != name: continue

        # Вычисление критерия правила предпочтения
        t = T[i][0] if rule else sum(T[i])
        # Получение времени обработки детали для рассматриваемого варианта
        d = T[i][0]
        
        # Если в начале выбора детали или критерий удовлетворяет - меняем параметр и значения
        if id == -1 or (t < value if rule else t > value):
            id = i
            value = t
            duration = d

    if id == -1:
        return
    
    # Добавляем в очередь нужную деталь
    pipeline.append((id, time + duration - 1))
    # Помечаем деталь как занятую
    busy[id] = True

    # Обновляем матрицы
    F[id] = F[id][1:]
    T[id] = T[id][1:]
        
# Предварительная загрузка станков деталями
for k in machines:
    dequeue(k, machines[k])

# Имитационная модель многоконвейерного производства по особым состояниям
is_not_empty = True
# Пока не обработали все детали
while is_not_empty:
    min_time = time

    # Для всех станков
    for i, k in enumerate(machines):
        machine = machines[k]

        # Если деталь обработана - убираем её со станка, помечаем как незанятую
        if len(machine) > 0 and (machine[0][1] < time):
            # Рассматриваем время смены детали как время системы в новом состоянии
            min_time = min(min_time, machine[0][1])
            _id, _ = machine.pop()
            busy[_id] = False

        # Если на станке нет детали (возможно деталь убрана на этой итерации), пробуем выбрать новую деталь
        if len(machine) == 0:
            dequeue(k, machine)

        # Помечаем особые состояния для построения графика Ганта
        if len(machine) > 0:
            _id = machine[0][0]
            plots[_id].append((time, i))
            plots[_id].append((time + 1, i))

            # Обновление времени пролеживания деталей на текущий момент
            prev_dt, total_di = detail_idles[_id]
            dd = time - prev_dt[0] if abs(prev_dt[1] - i) > 0 else 0
            detail_idles[_id] = ((time + 1, i), total_di + dd)

            # Обновление времени простоя станков на текущий момент
            prev_mt, total_mi = machine_idles[k]
            md = time - prev_mt[0] if (abs(prev_mt[1] - _id) > 0) else 0
            machine_idles[k] = ((time + 1, _id), total_mi + md)

    # Смещаем время в новое особое состояние
    time = min_time + 1
    is_not_empty = len([v for v in F if len(v) > 0]) > 0

# Помечаем состояние завершения обработки для построения графика Ганта
for i, k in enumerate(machines):
        machine = machines[k]

        if len(machine) > 0:
            _id, t = machine[0]
            if (t + 1 > time):
                time = t + 1
            plots[_id].append((t+1, i))

# Вывод параметров модели
time_table = PrettyTable()
time_table.add_column('Длительность производственного цикла T обработки всех деталей на всех станках (ед. вр.)', [time])
print(time_table.get_string())

machine_table = PrettyTable()
machine_ids = []
machine_delays = []
for k, v in machine_idles.items():
    machine_ids.append(k)
    machine_delays.append(v[1])

print('\n\nТаб. 1: Время простоя каждого станка')
machine_table.add_column('Идентификатор станка', machine_ids)
machine_table.add_column('Время простоя станка (ед. вр.)', machine_delays)
print(machine_table.get_string())

detail_table = PrettyTable()
detail_ids = []
detail_delays = []
for i, v in enumerate(detail_idles):
    detail_ids.append(i+1)
    detail_delays.append(v[1])

print('\n\nТаб. 2: Время "пролеживания" деталей в ожидании обработки на каждом станке')
detail_table.add_column('Номер детали', detail_ids)
detail_table.add_column('Время пролеживания детали (ед. вр.)', detail_delays)
print(detail_table.get_string())
print()

# Отрисовываем график при помощи библиотеки matplotlib
fig, ax = plt.subplots()

for i, p in enumerate(plots):
    ax.plot(sorted([v[0] for v in p]), [v[1] for v in p], label='деталь {}'.format(i+1))

ax.set_xlabel('время (ед. времени)')
ax.set_ylabel('станок')
ax.grid()

plt.yticks([0, 1, 2, 3, 4, 5], ['A', 'B', 'C', 'D', 'E', 'F'])
plt.gca().invert_yaxis()
plt.legend()

fig.savefig("gant.png")
plt.show()