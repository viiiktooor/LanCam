import io
import queue
import threading
import unittest
from unittest.mock import patch

from LanCamClient import LanCamApp, StreamWorker


class Root:
    def after(self, *args):
        pass


def app_without_window():
    app = LanCamApp.__new__(LanCamApp)
    app.root = Root()
    app.closing = False
    app.ui_events = queue.SimpleQueue()
    app.frame_lock = threading.Lock()
    app.pending_frame = None
    app.worker = object()
    return app


class StreamTests(unittest.TestCase):
    def test_latest_frame_replaces_backlog(self):
        app = app_without_window()
        seen = []
        app.on_frame = seen.append
        for i in range(1000):
            app.submit_frame(app.worker, str(i).encode())
        app.drain_ui()
        self.assertEqual(seen, [b'999'])

    def test_old_connection_cannot_change_ui_or_replace_new_frame(self):
        app = app_without_window()
        old = app.worker
        seen = []
        app.on_frame = seen.append
        app.ui(seen.append, 'old status', source=old)
        app.worker = object()
        app.submit_frame(app.worker, b'new')
        app.submit_frame(old, b'old')
        app.drain_ui()
        self.assertEqual(seen, [b'new'])

    def test_background_notifications_do_not_call_tk(self):
        app = app_without_window()
        seen = []
        t = threading.Thread(target=lambda: app.ui(seen.append, 'done'))
        t.start()
        t.join()
        self.assertEqual(seen, [])
        app.drain_ui()
        self.assertEqual(seen, ['done'])

    def test_split_jpeg_markers_and_multiple_frames(self):
        app = app_without_window()
        worker = StreamWorker(app, 'http://localhost:4747')
        frames = []
        app.submit_frame = lambda source, jpg: frames.append(jpg)
        chunks = iter([b'header\xff', b'\xd8one\xff', b'\xd9\xff\xd8two\xff\xd9', b''])

        class Response:
            def read1(self, size):
                return next(chunks)

        with self.assertRaises(ConnectionError):
            worker.read_mjpeg(Response())
        self.assertEqual(frames, [b'\xff\xd8one\xff\xd9', b'\xff\xd8two\xff\xd9'])

    def test_unterminated_frame_is_bounded(self):
        app = app_without_window()
        worker = StreamWorker(app, 'http://localhost:4747')
        with patch('LanCamClient.MAX_FRAME_BYTES', 32):
            with self.assertRaisesRegex(ConnectionError, 'limite'):
                worker.read_mjpeg(io.BytesIO(b'\xff\xd8' + b'x' * 100))

    def test_closed_app_drops_notifications(self):
        app = app_without_window()
        app.closing = True
        app.ui(lambda: self.fail('closed callback'))
        app.submit_frame(app.worker, b'image')
        app.drain_ui()
        self.assertTrue(app.ui_events.empty())
        self.assertIsNone(app.pending_frame)


if __name__ == '__main__':
    unittest.main()
