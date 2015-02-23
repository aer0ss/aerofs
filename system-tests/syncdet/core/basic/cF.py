from lib.files import instance_unique_path, wait_file_with_content

content = 'written by sys 0'

def put():
    print 'put', instance_unique_path()
    with open(instance_unique_path(), 'wb') as f:
        f.write(content)

def get():
    print 'get', instance_unique_path()
    wait_file_with_content(instance_unique_path(), content)


spec = { 'entries': [put], 'default': get, 'timeout': 8 }
