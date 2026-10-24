"""Lets the plugin be imported without AudioMuse-AI: a stand-in ``plugin.api`` and the import path."""

import logging
import os
import sys
import types

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

if "plugin.api" not in sys.modules:
    api = types.ModuleType("plugin.api")
    api.settings = {}
    api.queued = []
    api.logger = logging.getLogger("siansa_analysis.test")
    api.get_db = lambda: None
    api.table = lambda name: f"plugin_siansa_analysis__{name}"
    api.get_setting = lambda key, default=None: api.settings.get(key, default)
    api.set_setting = lambda key, value: api.settings.__setitem__(key, value)
    api.enqueue = lambda func, *a, **k: api.queued.append(func.__name__)
    api.active_server_id = lambda: None
    api.manage_plugins_url = lambda: "/plugins/manage"
    api.render_page = lambda body, title=None: f"<title>{title}</title>{body}"
    package = types.ModuleType("plugin")
    package.api = api
    sys.modules["plugin"] = package
    sys.modules["plugin.api"] = api
