# write a python script that fixes <time> tags in a gpx file that are formatted like '2023-08-26T13:14:31.012000+0200' to the UTC equivalent
# usage: python fix-gpx.py <input-file>

import sys
import re
import datetime

# read input file
with open(sys.argv[1], 'r') as f:
    gpx = f.read()

# find all <time> tags
times = re.findall(r'<time>(.*?)</time>', gpx)

# convert each time to UTC
for time in times:
    # convert to datetime object
    try:
      dt = datetime.datetime.strptime(time, '%Y-%m-%dT%H:%M:%S.%f%z')
    except ValueError:
      dt = datetime.datetime.strptime(time, '%Y-%m-%dT%H:%M:%S%z')
    # convert to UTC
    dt = dt.astimezone(datetime.timezone.utc)
    # convert back to string
    dt = dt.strftime('%Y-%m-%dT%H:%M:%SZ')
    # replace in gpx
    gpx = gpx.replace(time, dt)

# write back to file
with open(sys.argv[1], 'w') as f:
    f.write(gpx)

print('done')