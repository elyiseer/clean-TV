# Stats capacity and Hebrew layout

RAM shows available/total MB from MemAvailable and MemTotal in the same /proc/meminfo snapshot. Storage shows available/total GB on the Android data filesystem, rather than advertised hardware capacity. Zero available space remains visible; unreadable totals are omitted rather than fabricated. Existing translated labels and units are retained.

Hebrew stats use explicit RTL paragraph direction and LTR embeddings/directional marks around complete measurement ratios. Metric separators carry RTL marks. Isolate controls rendered incorrectly during Android TV testing, so embeddings are used instead.

The stats right inset matches the numeric clock. The Hebrew greeting tracks the numeric clock width and position; the single-line AM/PM label sits on the left without artificial letter spacing. Other languages retain their layout.

JVM tests cover unit formatting, zero/missing values, ratio direction and unchanged English. On-device bounds checks confirmed greeting centering and matching stats/clock right edges in both 12- and 24-hour formats. Original display preferences were restored after testing. Device-identifying records remain private.
