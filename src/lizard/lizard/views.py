from lizard import app

@app.route('/', methods=['GET'])
@app.route('/index', methods=['GET'])
def index():
    return "<html><body><h1>Hello, World</h1></body></html"
