# Сколько близнецов и соседей ожидать на реальных данных.
# Модель: ошибка попытки ~ нормальная с разбросом sigma мс, округление до
# клетки уровня (L3 = 1 мс). Близнец — |ошибка| совпала с предыдущей,
# сосед — отличается ровно на клетку. Победы (|e| = 0) исключаются, как в
# нативе.
import random, statistics

def run(sigma, n, cell=1.0, trials=400):
    tw, nb = [], []
    for _ in range(trials):
        errs = [round(random.gauss(0, sigma)/cell)*cell for _ in range(n)]
        t = nbc = 0
        for i in range(1, n):
            a, b = abs(errs[i-1]), abs(errs[i])
            if a == 0 or b == 0:      # победы не участвуют
                continue
            if abs(a-b) < cell/2: t += 1
            elif abs(abs(a-b) - cell) < cell/2: nbc += 1
        tw.append(t); nb.append(nbc)
    return statistics.mean(tw), statistics.mean(nb)

print(f"{'разброс':>9} {'близнецов':>10} {'соседей':>9}   (на 200 попыток, уровень 3)")
for s in (3, 5, 8, 12, 20, 35, 60):
    t, n = run(s, 200)
    print(f"{s:>7} мс {t:>10.1f} {n:>9.1f}")
print()
print("то же на 60 попытках — одна средняя сессия:")
for s in (3, 8, 20, 60):
    t, n = run(s, 60)
    print(f"{s:>7} мс {t:>10.1f} {n:>9.1f}")
